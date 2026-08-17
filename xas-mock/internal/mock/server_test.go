package mock

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"os/exec"
	"reflect"
	"strings"
	"testing"
	"time"
)

func TestScenarioIsReproducibleAndRecordingIsNotAlbum(t *testing.T) {
	scenario := DefaultScenario()
	first, err := NewState(scenario)
	if err != nil {
		t.Fatal(err)
	}
	second, err := NewState(scenario)
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(first.Snapshot(true, 0, 100), second.Snapshot(true, 0, 100)) {
		t.Fatal("same seed and scenario must produce equal state")
	}
	invalid := scenario
	invalid.Accounts[0].GalleryAlbums = append(invalid.Accounts[0].GalleryAlbums, GalleryAlbumSpec{AlbumID: -1, Name: "invalid"})
	if _, err := NewState(invalid); err == nil || !strings.Contains(err.Error(), "local recording normalization") {
		t.Fatalf("expected -1 album rejection, got %v", err)
	}
}

func TestCheckedInScenariosLoad(t *testing.T) {
	for _, path := range []string{
		"../../scenarios/default.json",
		"../../scenarios/memory-profile.json",
		"../../scenarios/memory-profile-small.json",
		"../../scenarios/memory-profile-exif.json",
		"../../scenarios/memory-profile-exif-small.json",
	} {
		scenario, err := LoadScenario(path)
		if err != nil {
			t.Fatalf("load %s: %v", path, err)
		}
		if _, err := NewState(scenario); err != nil {
			t.Fatalf("initialize %s: %v", path, err)
		}
	}
}

func TestCloudContractSeparatesGalleryAndRecordings(t *testing.T) {
	server, state := newTestServer(t)
	defer server.Close()
	cookie := cloudCookie(state, "mock-user")

	albumResponse := doRequest(t, http.MethodGet, server.URL+"/gallery/user/album/list?pageNum=0&pageSize=10", nil, cookie)
	if albumResponse.StatusCode != http.StatusOK {
		t.Fatalf("album status=%d", albumResponse.StatusCode)
	}
	var albumBody struct {
		Data struct {
			Albums []struct {
				AlbumID int64 `json:"albumId"`
			} `json:"albums"`
		} `json:"data"`
	}
	decodeResponse(t, albumResponse, &albumBody)
	for _, album := range albumBody.Data.Albums {
		if album.AlbumID == -1 {
			t.Fatal("remote album list must not contain local recording id -1")
		}
	}

	timelineResponse := doRequest(t, http.MethodGet, server.URL+"/gallery/user/timeline?albumId=-1", nil, cookie)
	if timelineResponse.StatusCode != http.StatusNotFound {
		t.Fatalf("timeline -1 status=%d", timelineResponse.StatusCode)
	}
	timelineResponse.Body.Close()

	recordingResponse := doRequest(t, http.MethodGet, server.URL+"/sfs/ns/recorder/dir/0/list?limit=500&offset=0", nil, cookie)
	if recordingResponse.StatusCode != http.StatusOK {
		t.Fatalf("recording status=%d", recordingResponse.StatusCode)
	}
	var recordingBody struct {
		Data struct {
			List []struct {
				ID int64 `json:"id"`
			} `json:"list"`
		} `json:"data"`
	}
	decodeResponse(t, recordingResponse, &recordingBody)
	if len(recordingBody.Data.List) != 1 || recordingBody.Data.List[0].ID != 201 {
		t.Fatalf("unexpected recordings: %+v", recordingBody)
	}
}

func TestMutationIsAtomicAndDeletedMediaReturns50050(t *testing.T) {
	server, state := newTestServer(t)
	defer server.Close()
	_, revision := state.Health()
	request := MutationRequest{ExpectedRevision: &revision, Operations: []MutationOperation{
		{Op: "addAssets", UserID: "mock-user", AlbumID: 1, Assets: []GalleryAssetSpec{{ID: 301, Type: "image", FileName: "new.jpg", DateTaken: 1714651200000, Size: 1024}}},
		{Op: "addRecordings", UserID: "mock-user", Recordings: []RecordingSpec{{ID: 302, FileName: "new.m4a", RecordingType: 3, CreateTime: 1714651200000, Size: 2048}}},
	}}
	body, _ := json.Marshal(request)
	response := doRequest(t, http.MethodPost, server.URL+"/_control/v1/mutations", bytes.NewReader(body), "")
	if response.StatusCode != http.StatusOK {
		t.Fatalf("mutation status=%d body=%s", response.StatusCode, readBody(response))
	}
	response.Body.Close()

	bad := MutationRequest{Operations: []MutationOperation{{Op: "addAssets", UserID: "mock-user", AlbumID: 1, Assets: []GalleryAssetSpec{{ID: 401, Type: "image", Size: 1}}}, {Op: "deleteRecordings", UserID: "mock-user", IDs: []int64{999999}}}}
	body, _ = json.Marshal(bad)
	response = doRequest(t, http.MethodPost, server.URL+"/_control/v1/mutations", bytes.NewReader(body), "")
	if response.StatusCode != http.StatusBadRequest {
		t.Fatalf("bad mutation status=%d", response.StatusCode)
	}
	response.Body.Close()
	account, _ := state.authenticate("mock-user", serviceToken(DefaultScenario().Seed, "mock-user"))
	if account.GalleryAlbums[1].Assets[401] != nil {
		t.Fatal("failed atomic mutation must not leave asset 401")
	}

	deleteRequest := MutationRequest{Operations: []MutationOperation{{Op: "deleteAssets", UserID: "mock-user", AlbumID: 1, IDs: []int64{301}}}}
	body, _ = json.Marshal(deleteRequest)
	response = doRequest(t, http.MethodPost, server.URL+"/_control/v1/mutations", bytes.NewReader(body), "")
	if response.StatusCode != http.StatusOK {
		t.Fatalf("delete status=%d", response.StatusCode)
	}
	response.Body.Close()
	cookie := cloudCookie(state, "mock-user")
	storage := doRequest(t, http.MethodGet, server.URL+"/gallery/storage?id=301", nil, cookie)
	if storage.StatusCode != http.StatusOK {
		t.Fatalf("deleted storage status=%d", storage.StatusCode)
	}
	var deleted struct {
		Code int `json:"code"`
	}
	decodeResponse(t, storage, &deleted)
	if deleted.Code != 50050 {
		t.Fatalf("deleted code=%d", deleted.Code)
	}
}

func TestMarkDeletedKeepsAssetInListAndMirrorsStorageErrorCodes(t *testing.T) {
	server, state := newTestServer(t)
	defer server.Close()
	mutate := func(ops ...MutationOperation) {
		t.Helper()
		body, _ := json.Marshal(MutationRequest{Operations: ops})
		response := doRequest(t, http.MethodPost, server.URL+"/_control/v1/mutations", bytes.NewReader(body), "")
		if response.StatusCode != http.StatusOK {
			t.Fatalf("mutation status=%d body=%s", response.StatusCode, readBody(response))
		}
		response.Body.Close()
	}
	mutate(
		MutationOperation{Op: "addAssets", UserID: "mock-user", AlbumID: 1, Assets: []GalleryAssetSpec{{ID: 303, Type: "image", FileName: "del.jpg", DateTaken: 1714651200000, Size: 1024}}},
		MutationOperation{Op: "addRecordings", UserID: "mock-user", Recordings: []RecordingSpec{{ID: 304, FileName: "del.m4a", RecordingType: 3, CreateTime: 1714651200000, Size: 2048}}},
		MutationOperation{Op: "markDeleted", UserID: "mock-user", AlbumID: 1, IDs: []int64{303}},
		MutationOperation{Op: "markDeleted", UserID: "mock-user", IDs: []int64{304}},
	)

	cookie := cloudCookie(state, "mock-user")

	// markDeleted 后资产仍保留在列表中（列表可见但无法下载）
	galleriesResponse := doRequest(t, http.MethodGet, server.URL+"/gallery/user/galleries?albumId=1&pageNum=0&pageSize=200", nil, cookie)
	var galleries struct {
		Data struct {
			Galleries []struct {
				ID int64 `json:"id"`
			} `json:"galleries"`
		} `json:"data"`
	}
	decodeResponse(t, galleriesResponse, &galleries)
	found := false
	for _, asset := range galleries.Data.Galleries {
		if asset.ID == 303 {
			found = true
		}
	}
	if !found {
		t.Fatal("markDeleted 后资产 303 应仍在相册列表中")
	}

	// 相册删除 → code=50050；录音删除 → code=50202；均为 result=error 且 retriable=false
	var galleryErr struct {
		Code      int    `json:"code"`
		Result    string `json:"result"`
		Retriable bool   `json:"retriable"`
	}
	galleryStorage := doRequest(t, http.MethodGet, server.URL+"/gallery/storage?id=303", nil, cookie)
	decodeResponse(t, galleryStorage, &galleryErr)
	if galleryErr.Code != 50050 || galleryErr.Result != "error" || galleryErr.Retriable {
		t.Fatalf("gallery deleted storage=%+v", galleryErr)
	}

	var recordingErr struct {
		Code      int    `json:"code"`
		Result    string `json:"result"`
		Retriable bool   `json:"retriable"`
	}
	recordingStorage := doRequest(t, http.MethodGet, server.URL+"/sfs/ns/recorder/file/304/cb/dl_sfs_cb_1_0/storage", nil, cookie)
	decodeResponse(t, recordingStorage, &recordingErr)
	if recordingErr.Code != 50202 || recordingErr.Result != "error" || recordingErr.Retriable {
		t.Fatalf("recording deleted storage=%+v", recordingErr)
	}
}

func TestSetStorageErrorConfiguresTransientErrorAndClearRecovers(t *testing.T) {
	server, state := newTestServer(t)
	defer server.Close()
	mutate := func(ops ...MutationOperation) {
		t.Helper()
		body, _ := json.Marshal(MutationRequest{Operations: ops})
		response := doRequest(t, http.MethodPost, server.URL+"/_control/v1/mutations", bytes.NewReader(body), "")
		if response.StatusCode != http.StatusOK {
			t.Fatalf("mutation status=%d body=%s", response.StatusCode, readBody(response))
		}
		response.Body.Close()
	}
	mutate(
		MutationOperation{Op: "addAssets", UserID: "mock-user", AlbumID: 1, Assets: []GalleryAssetSpec{{ID: 305, Type: "image", FileName: "busy.jpg", DateTaken: 1714651200000, Size: 1024}}},
		MutationOperation{Op: "setStorageError", UserID: "mock-user", AlbumID: 1, IDs: []int64{305}, Code: 50051, Retriable: true, Description: "系统繁忙，请稍后重试", Reason: "service busy"},
	)

	cookie := cloudCookie(state, "mock-user")

	// 瞬时错误：code != 0 且 retriable=true
	var transientErr struct {
		Code      int    `json:"code"`
		Result    string `json:"result"`
		Retriable bool   `json:"retriable"`
	}
	storage := doRequest(t, http.MethodGet, server.URL+"/gallery/storage?id=305", nil, cookie)
	decodeResponse(t, storage, &transientErr)
	if transientErr.Code != 50051 || transientErr.Result != "error" || !transientErr.Retriable {
		t.Fatalf("transient storage=%+v", transientErr)
	}

	// 资产仍可正常下载（storage 错误不影响列表）
	galleriesResponse := doRequest(t, http.MethodGet, server.URL+"/gallery/user/galleries?albumId=1&pageNum=0&pageSize=200", nil, cookie)
	var galleries struct {
		Data struct {
			Galleries []struct {
				ID int64 `json:"id"`
			} `json:"galleries"`
		} `json:"data"`
	}
	decodeResponse(t, galleriesResponse, &galleries)
	found := false
	for _, asset := range galleries.Data.Galleries {
		if asset.ID == 305 {
			found = true
		}
	}
	if !found {
		t.Fatal("setStorageError 后资产 305 应仍在相册列表中")
	}

	// code=0 清除错误后 storage 恢复正常
	mutate(MutationOperation{Op: "setStorageError", UserID: "mock-user", AlbumID: 1, IDs: []int64{305}, Code: 0})
	var recovered struct {
		Code int `json:"code"`
		Data struct {
			URL string `json:"url"`
		} `json:"data"`
	}
	storage = doRequest(t, http.MethodGet, server.URL+"/gallery/storage?id=305", nil, cookie)
	decodeResponse(t, storage, &recovered)
	if recovered.Code != 0 || recovered.Data.URL == "" {
		t.Fatalf("recovered storage=%+v", recovered)
	}
}

func TestDownloadStreamsExactLengthAndHonorsRateLimit(t *testing.T) {
	server, state := newTestServer(t)
	defer server.Close()
	cookie := cloudCookie(state, "mock-user")
	storage := doRequest(t, http.MethodGet, server.URL+"/gallery/storage?id=101", nil, cookie)
	var storageBody struct {
		Data struct {
			URL string `json:"url"`
		} `json:"data"`
	}
	decodeResponse(t, storage, &storageBody)
	oss := doRequest(t, http.MethodGet, storageBody.Data.URL, nil, "")
	ossBytes, _ := io.ReadAll(oss.Body)
	oss.Body.Close()
	jsonp := string(ossBytes)
	start, end := strings.IndexByte(jsonp, '('), strings.LastIndexByte(jsonp, ')')
	var signed struct{ URL, Meta string }
	if err := json.Unmarshal([]byte(jsonp[start+1:end]), &signed); err != nil {
		t.Fatal(err)
	}

	profileBody := `{"chunkSizeBytes":4,"bytesPerSecondPerDownload":100}`
	profile := doRequest(t, http.MethodPut, server.URL+"/_control/v1/network", strings.NewReader(profileBody), "")
	if profile.StatusCode != http.StatusOK {
		t.Fatalf("network status=%d", profile.StatusCode)
	}
	profile.Body.Close()
	form := url.Values{"meta": {signed.Meta}}
	started := time.Now()
	download := doRequest(t, http.MethodPost, signed.URL, strings.NewReader(form.Encode()), "Content-Type: application/x-www-form-urlencoded")
	content, _ := io.ReadAll(download.Body)
	download.Body.Close()
	if int64(len(content)) != int64(len("xiaomi-album-syncer-api-e2e\n")) {
		t.Fatalf("download size=%d", len(content))
	}
	if time.Since(started) < 200*time.Millisecond {
		t.Fatalf("rate limit was not applied: %s", time.Since(started))
	}
}

func TestPatternWriterUsesBoundedChunks(t *testing.T) {
	writer := &maxChunkWriter{}
	if err := writePattern(writer, 1, 99, 1, 16*1024*1024, "", 8192, 0); err != nil {
		t.Fatal(err)
	}
	if writer.total != 16*1024*1024 {
		t.Fatalf("total=%d", writer.total)
	}
	if writer.max > 8192 {
		t.Fatalf("max chunk=%d", writer.max)
	}
	var first, second bytes.Buffer
	if err := writePattern(&first, 1, 99, 1, 1024, "abc", 7, 0); err != nil {
		t.Fatal(err)
	}
	if err := writePattern(&second, 1, 99, 1, 1024, "abc", 31, 0); err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(first.Bytes(), second.Bytes()) {
		t.Fatal("download bytes must not depend on network chunk size")
	}
}

func TestJPEGWriterIsExactDeterministicAndBounded(t *testing.T) {
	const size = int64(16 * 1024 * 1024)
	writer := &maxChunkWriter{}
	if err := writeContent(writer, 7, 99, 1, size, "", "jpeg", 8192, 0); err != nil {
		t.Fatal(err)
	}
	if writer.total != size {
		t.Fatalf("total=%d", writer.total)
	}
	if writer.max > 8192 {
		t.Fatalf("max chunk=%d", writer.max)
	}

	var first, second bytes.Buffer
	jpegSize := int64(len(baseJPEG) + 80_000)
	if err := writeContent(&first, 7, 99, 1, jpegSize, "", "jpeg", 997, 0); err != nil {
		t.Fatal(err)
	}
	if err := writeContent(&second, 7, 99, 1, jpegSize, "", "jpeg", 32768, 0); err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(first.Bytes(), second.Bytes()) {
		t.Fatal("jpeg bytes must not depend on network chunk size")
	}
	if !bytes.HasPrefix(first.Bytes(), []byte{0xff, 0xd8}) || !bytes.HasSuffix(first.Bytes(), []byte{0xff, 0xd9}) {
		t.Fatal("jpeg content must preserve SOI and EOI markers")
	}
}

func TestJPEGContentCanBeReadAndUpdatedByExifTool(t *testing.T) {
	exifTool, err := exec.LookPath("exiftool")
	if err != nil {
		t.Skip("exiftool is not installed")
	}
	file, err := os.CreateTemp(t.TempDir(), "xiaomi-mock-*.jpg")
	if err != nil {
		t.Fatal(err)
	}
	if err := writeContent(file, 7, 99, 1, 2*1024*1024, "", "jpeg", 32768, 0); err != nil {
		t.Fatal(err)
	}
	if err := file.Close(); err != nil {
		t.Fatal(err)
	}
	output, err := exec.Command(exifTool, "-j", "-G", file.Name()).CombinedOutput()
	if err != nil || !bytes.Contains(output, []byte(`"File:FileType": "JPEG"`)) {
		t.Fatalf("exiftool read failed: %v\n%s", err, output)
	}
	output, err = exec.Command(
		exifTool,
		"-overwrite_original",
		"-DateTimeOriginal=2026:07:16 12:00:00",
		file.Name(),
	).CombinedOutput()
	if err != nil {
		t.Fatalf("exiftool update failed: %v\n%s", err, output)
	}
}

type maxChunkWriter struct {
	total int64
	max   int
}

func (w *maxChunkWriter) Write(p []byte) (int, error) {
	w.total += int64(len(p))
	if len(p) > w.max {
		w.max = len(p)
	}
	return len(p), nil
}

func newTestServer(t *testing.T) (*httptest.Server, *State) {
	t.Helper()
	state, err := NewState(DefaultScenario())
	if err != nil {
		t.Fatal(err)
	}
	return httptest.NewServer(NewServer(state, "")), state
}

func cloudCookie(state *State, userID string) string {
	seed, _ := state.Health()
	return "userId=" + userID + "; serviceToken=" + serviceToken(seed, userID)
}

func doRequest(t *testing.T, method, target string, body io.Reader, cookieOrHeader string) *http.Response {
	t.Helper()
	request, err := http.NewRequest(method, target, body)
	if err != nil {
		t.Fatal(err)
	}
	if strings.HasPrefix(cookieOrHeader, "Content-Type:") {
		request.Header.Set("Content-Type", strings.TrimSpace(strings.TrimPrefix(cookieOrHeader, "Content-Type:")))
	} else if cookieOrHeader != "" {
		request.Header.Set("Cookie", cookieOrHeader)
	}
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		t.Fatal(err)
	}
	return response
}

func decodeResponse(t *testing.T, response *http.Response, target any) {
	t.Helper()
	defer response.Body.Close()
	if err := json.NewDecoder(response.Body).Decode(target); err != nil {
		t.Fatal(err)
	}
}

func readBody(response *http.Response) string {
	body, _ := io.ReadAll(response.Body)
	return string(body)
}
