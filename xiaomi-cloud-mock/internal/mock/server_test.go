package mock

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
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
	for _, path := range []string{"../../scenarios/default.json", "../../scenarios/memory-profile.json"} {
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
