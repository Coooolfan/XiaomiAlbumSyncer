package mock

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"sort"
	"strconv"
	"strings"
	"time"
)

type Server struct {
	state     *State
	publicURL string
	mux       *http.ServeMux
}

func NewServer(state *State, publicURL string) http.Handler {
	server := &Server{state: state, publicURL: strings.TrimRight(publicURL, "/"), mux: http.NewServeMux()}
	server.routes()
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		state.stats.RecordRoute(r.URL.Path)
		server.mux.ServeHTTP(w, r)
	})
}

func (s *Server) routes() {
	s.mux.HandleFunc("GET /api/user/login", s.preLogin)
	s.mux.HandleFunc("GET /mock/login", s.loginRedirect)
	s.mux.HandleFunc("GET /mock/token", s.issueToken)
	s.mux.HandleFunc("GET /gallery/user/album/list", s.albumList)
	s.mux.HandleFunc("GET /gallery/user/galleries", s.galleryList)
	s.mux.HandleFunc("GET /gallery/user/timeline", s.timeline)
	s.mux.HandleFunc("GET /sfs/ns/recorder/dir/0/list", s.recordingList)
	s.mux.HandleFunc("GET /gallery/storage", s.galleryStorage)
	s.mux.HandleFunc("GET /sfs/ns/recorder/file/", s.recordingStorage)
	s.mux.HandleFunc("GET /mock/oss/", s.oss)
	s.mux.HandleFunc("POST /mock/download/", s.download)
	s.mux.HandleFunc("GET /_control/v1/health", s.health)
	s.mux.HandleFunc("POST /_control/v1/reset", s.reset)
	s.mux.HandleFunc("GET /_control/v1/state", s.controlState)
	s.mux.HandleFunc("POST /_control/v1/mutations", s.mutations)
	s.mux.HandleFunc("PUT /_control/v1/network", s.network)
	s.mux.HandleFunc("GET /_control/v1/stats", s.controlStats)
	s.mux.HandleFunc("POST /_control/v1/notify", s.notify)
	s.mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		s.state.stats.Unexpected()
		writeError(w, http.StatusNotFound, 40400, "route not found")
	})
}

func (s *Server) preLogin(w http.ResponseWriter, r *http.Request) {
	userID, passToken := cookieValue(r, "userId"), cookieValue(r, "passToken")
	if userID == "" || passToken == "" || !strings.HasPrefix(cookieValue(r, "deviceId"), "wb_") {
		writeError(w, http.StatusUnauthorized, 40100, "invalid login cookies")
		return
	}
	if _, ok := s.state.login(userID, passToken); !ok {
		writeError(w, http.StatusUnauthorized, 40101, "invalid credentials")
		return
	}
	loginURL := s.baseURL(r) + "/mock/login?userId=" + url.QueryEscape(userID)
	writeJSON(w, http.StatusOK, map[string]any{"data": map[string]any{"loginUrl": loginURL}})
}

func (s *Server) loginRedirect(w http.ResponseWriter, r *http.Request) {
	userID := r.URL.Query().Get("userId")
	if _, ok := s.state.login(userID, cookieValue(r, "passToken")); !ok || cookieValue(r, "userId") != userID {
		writeError(w, http.StatusUnauthorized, 40101, "invalid credentials")
		return
	}
	w.Header().Set("Location", s.baseURL(r)+"/mock/token?userId="+url.QueryEscape(userID))
	w.WriteHeader(http.StatusFound)
}

func (s *Server) issueToken(w http.ResponseWriter, r *http.Request) {
	userID := r.URL.Query().Get("userId")
	account, ok := s.state.login(userID, cookieValue(r, "passToken"))
	if !ok || cookieValue(r, "userId") != userID {
		writeError(w, http.StatusUnauthorized, 40101, "invalid credentials")
		return
	}
	http.SetCookie(w, &http.Cookie{Name: "serviceToken", Value: account.ServiceToken, Path: "/", HttpOnly: true})
	writeJSON(w, http.StatusOK, map[string]any{})
}

func (s *Server) albumList(w http.ResponseWriter, r *http.Request) {
	account, ok := s.cloudAccount(w, r)
	if !ok {
		return
	}
	page, size, ok := pageParams(w, r, "pageNum", "pageSize")
	if !ok {
		return
	}
	ids := make([]int64, 0, len(account.GalleryAlbums))
	for id := range account.GalleryAlbums {
		ids = append(ids, id)
	}
	sort.Slice(ids, func(i, j int) bool { return ids[i] < ids[j] })
	start := page * size
	end := min(start+size, len(ids))
	albums := make([]map[string]any, 0)
	if start < len(ids) {
		for _, id := range ids[start:end] {
			album := account.GalleryAlbums[id]
			albums = append(albums, map[string]any{"albumId": album.AlbumID, "name": album.Name, "mediaCount": len(album.Assets), "lastUpdateTime": album.LastUpdateTime})
		}
	}
	writeJSON(w, http.StatusOK, map[string]any{"data": map[string]any{"albums": albums, "isLastPage": end >= len(ids)}})
}

func (s *Server) galleryList(w http.ResponseWriter, r *http.Request) {
	account, ok := s.cloudAccount(w, r)
	if !ok {
		return
	}
	albumID, err := requiredInt64(r, "albumId")
	if err != nil || albumID == -1 {
		writeError(w, http.StatusBadRequest, 40001, "invalid albumId")
		return
	}
	album := account.GalleryAlbums[albumID]
	if album == nil {
		writeError(w, http.StatusNotFound, 40401, "album not found")
		return
	}
	page, size, ok := pageParams(w, r, "pageNum", "pageSize")
	if !ok {
		return
	}
	assets := sortedGalleryAssets(album.Assets)
	startDate, endDate := r.URL.Query().Get("startDate"), r.URL.Query().Get("endDate")
	if startDate != "" || endDate != "" {
		if len(startDate) != 8 || len(endDate) != 8 {
			writeError(w, http.StatusBadRequest, 40002, "startDate and endDate must use yyyyMMdd")
			return
		}
		filtered := assets[:0]
		for _, asset := range assets {
			day := time.UnixMilli(asset.DateTaken).UTC().Format("20060102")
			if day >= startDate && day <= endDate {
				filtered = append(filtered, asset)
			}
		}
		assets = filtered
	}
	start := page * size
	end := min(start+size, len(assets))
	rows := make([]map[string]any, 0)
	if start < len(assets) {
		for _, asset := range assets[start:end] {
			rows = append(rows, galleryAssetJSON(asset))
		}
	}
	writeJSON(w, http.StatusOK, map[string]any{"data": map[string]any{"galleries": rows, "isLastPage": end >= len(assets)}})
}

func (s *Server) timeline(w http.ResponseWriter, r *http.Request) {
	account, ok := s.cloudAccount(w, r)
	if !ok {
		return
	}
	albumIDText := r.URL.Query().Get("albumId")
	s.state.stats.RecordTimeline(albumIDText)
	albumID, err := strconv.ParseInt(albumIDText, 10, 64)
	if err != nil || albumID == -1 {
		writeError(w, http.StatusNotFound, 40401, "gallery album not found")
		return
	}
	album := account.GalleryAlbums[albumID]
	if album == nil {
		writeError(w, http.StatusNotFound, 40401, "gallery album not found")
		return
	}
	counts := map[string]int64{}
	hash := sha256.New()
	for _, asset := range sortedGalleryAssets(album.Assets) {
		day := time.UnixMilli(asset.DateTaken).UTC().Format("20060102")
		counts[day]++
		fmt.Fprintf(hash, "%d:%d:%d:%s;", asset.ID, asset.DateTaken, asset.Size, asset.SHA1)
	}
	writeJSON(w, http.StatusOK, map[string]any{"data": map[string]any{"indexHash": hex.EncodeToString(hash.Sum(nil)), "dayCount": counts}})
}

func (s *Server) recordingList(w http.ResponseWriter, r *http.Request) {
	account, ok := s.cloudAccount(w, r)
	if !ok {
		return
	}
	limit, err1 := optionalNonNegativeInt(r, "limit", 500)
	offset, err2 := optionalNonNegativeInt(r, "offset", 0)
	if err1 != nil || err2 != nil || limit <= 0 {
		writeError(w, http.StatusBadRequest, 40003, "invalid limit or offset")
		return
	}
	recordings := sortedRecordings(account.Recordings)
	end := min(offset+limit, len(recordings))
	rows := make([]map[string]any, 0)
	if offset < len(recordings) {
		for _, recording := range recordings[offset:end] {
			rows = append(rows, map[string]any{"id": recording.ID, "name": recording.RawName, "create_time": recording.CreateTime, "sha1": recording.SHA1, "size": recording.Size})
		}
	}
	writeJSON(w, http.StatusOK, map[string]any{"data": map[string]any{"list": rows}})
}

func (s *Server) galleryStorage(w http.ResponseWriter, r *http.Request) {
	account, ok := s.cloudAccount(w, r)
	if !ok {
		return
	}
	id, err := requiredInt64(r, "id")
	if err != nil {
		writeError(w, http.StatusBadRequest, 40004, "invalid id")
		return
	}
	for _, album := range account.GalleryAlbums {
		if asset := album.Assets[id]; asset != nil {
			s.writeStorage(w, r, signedMedia{ID: asset.ID, Kind: "gallery", Size: asset.Size, MimeType: asset.MimeType, Version: asset.Version, Pattern: asset.ContentPattern, ContentMode: asset.ContentMode})
			return
		}
	}
	if deleted, exists := s.state.deleted(id); exists && deleted.UserID == account.UserID && deleted.Kind == "gallery" {
		writeJSON(w, http.StatusOK, map[string]any{"code": 50050, "message": "media deleted"})
		return
	}
	writeError(w, http.StatusNotFound, 40402, "gallery asset not found")
}

func (s *Server) recordingStorage(w http.ResponseWriter, r *http.Request) {
	account, ok := s.cloudAccount(w, r)
	if !ok {
		return
	}
	parts := strings.Split(strings.TrimPrefix(r.URL.Path, "/sfs/ns/recorder/file/"), "/")
	if len(parts) != 4 || parts[1] != "cb" || parts[3] != "storage" {
		s.state.stats.Unexpected()
		writeError(w, http.StatusNotFound, 40400, "route not found")
		return
	}
	id, err := strconv.ParseInt(parts[0], 10, 64)
	if err != nil {
		writeError(w, http.StatusBadRequest, 40004, "invalid recording id")
		return
	}
	if recording := account.Recordings[id]; recording != nil {
		s.writeStorage(w, r, signedMedia{ID: recording.ID, Kind: "recording", Size: recording.Size, MimeType: "audio/mp4", Version: recording.Version, Pattern: recording.ContentPattern})
		return
	}
	if deleted, exists := s.state.deleted(id); exists && deleted.UserID == account.UserID && deleted.Kind == "recording" {
		writeJSON(w, http.StatusOK, map[string]any{"code": 50050, "message": "media deleted"})
		return
	}
	writeError(w, http.StatusNotFound, 40403, "recording not found")
}

type signedMedia struct {
	ID          int64
	Kind        string
	Size        int64
	MimeType    string
	Version     int64
	Pattern     string
	ContentMode string
}

func (s *Server) writeStorage(w http.ResponseWriter, r *http.Request, media signedMedia) {
	query := url.Values{"kind": {media.Kind}, "size": {strconv.FormatInt(media.Size, 10)}, "version": {strconv.FormatInt(media.Version, 10)}, "mime": {media.MimeType}, "pattern": {media.Pattern}, "contentMode": {media.ContentMode}}
	query.Set("sig", s.signature(media.ID, query))
	writeJSON(w, http.StatusOK, map[string]any{"code": 0, "data": map[string]any{"url": fmt.Sprintf("%s/mock/oss/%d?%s", s.baseURL(r), media.ID, query.Encode())}})
}

func (s *Server) oss(w http.ResponseWriter, r *http.Request) {
	id, params, ok := s.signedRequest(w, r, "/mock/oss/")
	if !ok {
		return
	}
	meta := params.Get("sig")
	downloadURL := fmt.Sprintf("%s/mock/download/%d?%s", s.baseURL(r), id, params.Encode())
	body, _ := json.Marshal(map[string]any{"url": downloadURL, "meta": meta})
	w.Header().Set("Content-Type", "application/javascript; charset=utf-8")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte("dl_callback("))
	_, _ = w.Write(body)
	_, _ = w.Write([]byte(")"))
}

func (s *Server) download(w http.ResponseWriter, r *http.Request) {
	id, params, ok := s.signedRequest(w, r, "/mock/download/")
	if !ok {
		return
	}
	if err := r.ParseForm(); err != nil || r.Form.Get("meta") != params.Get("sig") {
		writeError(w, http.StatusBadRequest, 40005, "invalid download meta")
		return
	}
	size, _ := strconv.ParseInt(params.Get("size"), 10, 64)
	version, _ := strconv.ParseInt(params.Get("version"), 10, 64)
	profile := s.state.networkFor(id)
	if profile.ResponseDelayMs > 0 {
		timer := time.NewTimer(time.Duration(profile.ResponseDelayMs) * time.Millisecond)
		select {
		case <-timer.C:
		case <-r.Context().Done():
			timer.Stop()
			return
		}
	}
	w.Header().Set("Content-Type", params.Get("mime"))
	w.Header().Set("Content-Length", strconv.FormatInt(size, 10))
	w.WriteHeader(http.StatusOK)
	s.state.stats.BeginDownload()
	counting := &countingWriter{Writer: w}
	err := streamContent(counting, s.seed(), id, version, size, params.Get("pattern"), params.Get("contentMode"), profile.ChunkSizeBytes, profile.BytesPerSecond, r.Context())
	s.state.stats.EndDownload(counting.N)
	if err != nil && !errors.Is(err, r.Context().Err()) {
		s.state.stats.Unexpected()
	}
}

type countingWriter struct {
	Writer http.ResponseWriter
	N      int64
}

func (w *countingWriter) Write(p []byte) (int, error) {
	n, err := w.Writer.Write(p)
	w.N += int64(n)
	return n, err
}

func (s *Server) signedRequest(w http.ResponseWriter, r *http.Request, prefix string) (int64, url.Values, bool) {
	id, err := strconv.ParseInt(strings.TrimPrefix(r.URL.Path, prefix), 10, 64)
	if err != nil {
		writeError(w, http.StatusBadRequest, 40004, "invalid media id")
		return 0, nil, false
	}
	params := r.URL.Query()
	sig := params.Get("sig")
	paramsWithoutSig := cloneValues(params)
	paramsWithoutSig.Del("sig")
	if !hmac.Equal([]byte(sig), []byte(s.signature(id, paramsWithoutSig))) {
		writeError(w, http.StatusForbidden, 40300, "invalid signature")
		return 0, nil, false
	}
	return id, params, true
}

func (s *Server) signature(id int64, params url.Values) string {
	mac := hmac.New(sha256.New, []byte(fmt.Sprintf("mock-signing-key:%d", s.seed())))
	fmt.Fprintf(mac, "%d?%s", id, params.Encode())
	return hex.EncodeToString(mac.Sum(nil))
}

func (s *Server) seed() int64 { seed, _ := s.state.Health(); return seed }

func (s *Server) health(w http.ResponseWriter, _ *http.Request) {
	seed, revision := s.state.Health()
	writeJSON(w, http.StatusOK, map[string]any{"ready": true, "seed": seed, "revision": revision})
}

func (s *Server) reset(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Seed *int64 `json:"seed,omitempty"`
	}
	if err := decodeJSON(r, &body); err != nil {
		writeError(w, http.StatusBadRequest, 40010, err.Error())
		return
	}
	if err := s.state.Reset(body.Seed); err != nil {
		writeError(w, http.StatusBadRequest, 40011, err.Error())
		return
	}
	seed, revision := s.state.Health()
	writeJSON(w, http.StatusOK, map[string]any{"seed": seed, "revision": revision})
}

func (s *Server) controlState(w http.ResponseWriter, r *http.Request) {
	includeMedia, _ := strconv.ParseBool(r.URL.Query().Get("includeMedia"))
	page, _ := optionalNonNegativeInt(r, "mediaPage", 0)
	size, _ := optionalNonNegativeInt(r, "mediaPageSize", 100)
	writeJSON(w, http.StatusOK, s.state.Snapshot(includeMedia, page, size))
}

func (s *Server) mutations(w http.ResponseWriter, r *http.Request) {
	var request MutationRequest
	if err := decodeJSON(r, &request); err != nil {
		writeError(w, http.StatusBadRequest, 40010, err.Error())
		return
	}
	result, err := s.state.Mutate(request)
	if errors.Is(err, ErrRevisionConflict) {
		writeError(w, http.StatusConflict, 40900, err.Error())
		return
	}
	if err != nil {
		writeError(w, http.StatusBadRequest, 40012, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, result)
}

func (s *Server) network(w http.ResponseWriter, r *http.Request) {
	var profile NetworkProfile
	if err := decodeJSON(r, &profile); err != nil {
		writeError(w, http.StatusBadRequest, 40010, err.Error())
		return
	}
	revision := s.state.UpdateNetwork(profile)
	writeJSON(w, http.StatusOK, map[string]any{"revision": revision, "network": normalizeNetwork(profile)})
}

func (s *Server) controlStats(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, s.state.stats.Snapshot())
}

func (s *Server) notify(w http.ResponseWriter, r *http.Request) {
	if r.ContentLength == 0 {
		writeError(w, http.StatusBadRequest, 40013, "notification body is required")
		return
	}
	s.state.stats.Notify()
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) cloudAccount(w http.ResponseWriter, r *http.Request) (*Account, bool) {
	userID, token := cookieValue(r, "userId"), cookieValue(r, "serviceToken")
	account, ok := s.state.authenticate(userID, token)
	if !ok {
		writeError(w, http.StatusUnauthorized, 40102, "invalid service token")
	}
	return account, ok
}

func (s *Server) baseURL(r *http.Request) string {
	if s.publicURL != "" {
		return s.publicURL
	}
	scheme := "http"
	if r.TLS != nil {
		scheme = "https"
	}
	if forwarded := r.Header.Get("X-Forwarded-Proto"); forwarded != "" {
		scheme = forwarded
	}
	return scheme + "://" + r.Host
}

func pageParams(w http.ResponseWriter, r *http.Request, pageKey, sizeKey string) (int, int, bool) {
	page, err1 := optionalNonNegativeInt(r, pageKey, 0)
	size, err2 := optionalNonNegativeInt(r, sizeKey, 10)
	if err1 != nil || err2 != nil || size <= 0 || size > 10000 {
		writeError(w, http.StatusBadRequest, 40003, "invalid pagination")
		return 0, 0, false
	}
	return page, size, true
}

func optionalNonNegativeInt(r *http.Request, key string, fallback int) (int, error) {
	value := r.URL.Query().Get(key)
	if value == "" {
		return fallback, nil
	}
	parsed, err := strconv.Atoi(value)
	if err != nil || parsed < 0 {
		return 0, fmt.Errorf("invalid %s", key)
	}
	return parsed, nil
}

func requiredInt64(r *http.Request, key string) (int64, error) {
	value := r.URL.Query().Get(key)
	if value == "" {
		return 0, fmt.Errorf("missing %s", key)
	}
	return strconv.ParseInt(value, 10, 64)
}

func cookieValue(r *http.Request, name string) string {
	cookie, err := r.Cookie(name)
	if err != nil {
		return ""
	}
	return cookie.Value
}

func galleryAssetJSON(asset *GalleryAsset) map[string]any {
	return map[string]any{"id": asset.ID, "fileName": asset.FileName, "type": asset.Type, "dateTaken": asset.DateTaken, "sha1": asset.SHA1, "mimeType": asset.MimeType, "title": asset.Title, "size": asset.Size}
}

func decodeJSON(r *http.Request, target any) error {
	defer r.Body.Close()
	decoder := json.NewDecoder(r.Body)
	decoder.DisallowUnknownFields()
	return decoder.Decode(target)
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}

func writeError(w http.ResponseWriter, status, code int, message string) {
	writeJSON(w, status, map[string]any{"code": code, "message": message})
}

func cloneValues(values url.Values) url.Values {
	result := url.Values{}
	for key, entries := range values {
		result[key] = append([]string(nil), entries...)
	}
	return result
}
