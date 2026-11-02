package mock

import (
	"crypto/sha1"
	"encoding/base64"
	"encoding/json"
	"net/http"
	"strconv"
	"strings"
	"sync"
)

const icloudMFACode = "123456"
const icloudCloudPrefix = "/database/1/com.apple.photos.cloud/production/private/"

// icloudLibrary 实现当前服务端实际调用的 Apple 登录、CloudKit 照片和原件下载。
type icloudLibrary struct {
	mu           sync.Mutex
	trusted      bool
	codeAccepted bool
	expiredOnce  bool
	base         func(*http.Request) string
}

func newIcloudLibrary(base func(*http.Request) string) *icloudLibrary {
	return &icloudLibrary{base: base}
}

func (l *icloudLibrary) reset() {
	l.mu.Lock()
	defer l.mu.Unlock()
	l.trusted = false
	l.codeAccepted = false
	l.expiredOnce = false
}

func (l *icloudLibrary) register(mux *http.ServeMux) {
	mux.HandleFunc("POST /appleauth/auth/signin/init", l.signinInit)
	mux.HandleFunc("POST /appleauth/auth/signin/complete", l.signinComplete)
	mux.HandleFunc("PUT /appleauth/auth/verify/trusteddevice/securitycode", l.requestCode)
	mux.HandleFunc("POST /appleauth/auth/verify/trusteddevice/securitycode", l.submitCode)
	mux.HandleFunc("GET /appleauth/auth/2sv/trust", l.trust)
	mux.HandleFunc("POST /setup/ws/1/accountLogin", l.account)
	mux.HandleFunc("POST /setup/ws/1/validate", l.validate)
	mux.HandleFunc("POST "+icloudCloudPrefix, l.cloud)
	mux.HandleFunc("GET /icloud/download/", l.download)
}

func (l *icloudLibrary) signinInit(w http.ResponseWriter, _ *http.Request) {
	l.writeAuth(w, false)
	writeJSON(w, http.StatusOK, map[string]any{
		"salt":      base64.StdEncoding.EncodeToString([]byte{1, 2, 3, 4}),
		"b":         base64.StdEncoding.EncodeToString([]byte{2}),
		"c":         "challenge",
		"iteration": 1000,
		"protocol":  "s2k",
	})
}

func (l *icloudLibrary) signinComplete(w http.ResponseWriter, _ *http.Request) {
	l.mu.Lock()
	trusted := l.trusted
	l.mu.Unlock()
	l.writeAuth(w, trusted)
	if !trusted {
		writeJSON(w, http.StatusConflict, map[string]any{"authType": "hsa2"})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{})
}

func (l *icloudLibrary) requestCode(w http.ResponseWriter, _ *http.Request) {
	l.writeAuth(w, false)
	writeJSON(w, http.StatusOK, map[string]any{})
}

func (l *icloudLibrary) submitCode(w http.ResponseWriter, r *http.Request) {
	body, err := readJSONMap(r)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]any{"serverErrorCode": "BAD_REQUEST"})
		return
	}
	code, _ := dig(body, "securityCode", "code").(string)
	if code != icloudMFACode {
		writeJSON(w, http.StatusUnauthorized, map[string]any{})
		return
	}
	l.mu.Lock()
	l.codeAccepted = true
	l.mu.Unlock()
	l.writeAuth(w, false)
	writeJSON(w, http.StatusOK, map[string]any{})
}

func (l *icloudLibrary) trust(w http.ResponseWriter, _ *http.Request) {
	l.mu.Lock()
	accepted := l.codeAccepted
	if accepted {
		l.trusted = true
	}
	l.mu.Unlock()
	if !accepted {
		writeJSON(w, http.StatusConflict, map[string]any{"serverErrorCode": "MFA_REQUIRED"})
		return
	}
	l.writeAuth(w, true)
	w.Header().Set("X-Apple-TwoSV-Trust-Token", "mock-trust-token")
	writeJSON(w, http.StatusOK, map[string]any{})
}

func (l *icloudLibrary) account(w http.ResponseWriter, r *http.Request) {
	http.SetCookie(w, &http.Cookie{Name: "auth", Value: "secret-cookie", Path: "/", HttpOnly: true})
	l.writeAuth(w, true)
	l.writeAccountData(w, r)
}

func (l *icloudLibrary) validate(w http.ResponseWriter, r *http.Request) {
	if cookieValue(r, "auth") != "secret-cookie" {
		writeJSON(w, http.StatusUnauthorized, map[string]any{})
		return
	}
	l.writeAccountData(w, r)
}

func (l *icloudLibrary) writeAccountData(w http.ResponseWriter, r *http.Request) {
	l.mu.Lock()
	trusted := l.trusted
	l.mu.Unlock()
	writeJSON(w, http.StatusOK, map[string]any{
		"dsInfo":               map[string]any{"hsaVersion": 2, "dsid": "mock-dsid"},
		"hsaChallengeRequired": !trusted,
		"hsaTrustedBrowser":    trusted,
		"domainToUse":          "",
		"webservices":          map[string]any{"ckdatabasews": map[string]any{"url": l.base(r)}},
	})
}

func (l *icloudLibrary) cloud(w http.ResponseWriter, r *http.Request) {
	body, err := readJSONMap(r)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]any{"serverErrorCode": "BAD_REQUEST"})
		return
	}
	base := l.base(r)
	switch strings.TrimPrefix(r.URL.Path, icloudCloudPrefix) {
	case "zones/list":
		writeJSON(w, http.StatusOK, map[string]any{"zones": []any{map[string]any{
			"deleted": false, "zoneID": map[string]any{"zoneName": "PrimarySync"},
		}}})
	case "records/query":
		writeJSON(w, http.StatusOK, l.query(body, base))
	case "records/lookup":
		writeJSON(w, http.StatusOK, l.lookup(body, base))
	case "changes/zone":
		writeJSON(w, http.StatusOK, l.changes(body))
	case "internal/records/query/batch":
		writeJSON(w, http.StatusOK, l.batch(body))
	default:
		writeJSON(w, http.StatusNotFound, map[string]any{"serverErrorCode": "NOT_FOUND"})
	}
}

func (l *icloudLibrary) query(body map[string]any, base string) any {
	if jsonInt(body["resultsLimit"]) == 2 {
		return map[string]any{"syncToken": "base-1", "records": []any{}}
	}
	recordType, _ := dig(body, "query", "recordType").(string)
	switch recordType {
	case "CheckIndexingState":
		return map[string]any{"records": []any{ckRecord("CheckIndexingState", "index", map[string]any{"state": "FINISHED"})}}
	case "CPLAlbumByPositionLive":
		return map[string]any{"records": []any{l.albumRecord()}}
	case "CPLAssetAndMasterByAssetDateWithoutHiddenOrDeleted", "CPLContainerRelationLiveByAssetDate":
		if queryRank(body) > 0 {
			return map[string]any{"records": []any{}}
		}
		return map[string]any{"records": []any{l.assetRecord("asset-1"), l.masterRecord("asset-1", base)}}
	default:
		return map[string]any{"records": []any{}}
	}
}

func (l *icloudLibrary) lookup(body map[string]any, base string) any {
	requested, _ := body["records"].([]any)
	records := make([]any, 0, len(requested))
	for _, item := range requested {
		name, _ := dig(item, "recordName").(string)
		if record, ok := l.namedRecord(name, base); ok {
			records = append(records, record)
			continue
		}
		records = append(records, map[string]any{"recordName": name, "serverErrorCode": "NOT_FOUND"})
	}
	return map[string]any{"records": records}
}

func (l *icloudLibrary) changes(body map[string]any) any {
	token, _ := dig(body, "zones").([]any)
	syncToken := ""
	if len(token) > 0 {
		syncToken, _ = dig(token[0], "syncToken").(string)
	}
	switch syncToken {
	case "base-1":
		return zonePage("page-2", true, []any{l.assetRecord("asset-2"), l.relationRecord("asset-2")})
	case "page-2":
		return zonePage("caught-up", false, []any{})
	case "caught-up":
		l.mu.Lock()
		first := !l.expiredOnce
		if first {
			l.expiredOnce = true
		}
		l.mu.Unlock()
		if first {
			return map[string]any{"serverErrorCode": "CHANGE_TOKEN_EXPIRED"}
		}
		return zonePage("caught-up", false, []any{})
	default:
		return map[string]any{"serverErrorCode": "INVALID_SYNC_TOKEN"}
	}
}

func (l *icloudLibrary) batch(body map[string]any) any {
	queries, _ := body["batch"].([]any)
	batch := make([]any, 0, len(queries))
	for range queries {
		batch = append(batch, map[string]any{"records": []any{ckRecord("HyperionIndexCountLookup", "count", map[string]any{"itemCount": 1})}})
	}
	return map[string]any{"batch": batch}
}

func (l *icloudLibrary) download(w http.ResponseWriter, r *http.Request) {
	parts := strings.Split(strings.TrimPrefix(r.URL.Path, "/icloud/download/"), "/")
	if len(parts) != 2 {
		writeJSON(w, http.StatusNotFound, map[string]any{"serverErrorCode": "NOT_FOUND"})
		return
	}
	content := icloudContent(parts[0], parts[1])
	if content == nil {
		writeJSON(w, http.StatusNotFound, map[string]any{"serverErrorCode": "NOT_FOUND"})
		return
	}
	w.Header().Set("Content-Type", "application/octet-stream")
	w.Header().Set("Content-Length", strconv.Itoa(len(content)))
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(content)
}

func (l *icloudLibrary) namedRecord(name, base string) (any, bool) {
	switch {
	case name == "asset-1" || name == "asset-2":
		return l.assetRecord(name), true
	case strings.HasPrefix(name, "master-"):
		id := strings.TrimPrefix(name, "master-")
		if icloudContent(id, "resOriginal") == nil {
			return nil, false
		}
		return l.masterRecord(id, base), true
	case name == "asset-2-IN-album-trip":
		return l.relationRecord("asset-2"), true
	default:
		return nil, false
	}
}

func (l *icloudLibrary) assetRecord(id string) map[string]any {
	return ckRecord("CPLAsset", id, map[string]any{
		"masterRef":  map[string]any{"recordName": "master-" + id},
		"assetDate":  1714564800000,
		"isHidden":   false,
		"isFavorite": false,
	})
}

func (l *icloudLibrary) masterRecord(id, base string) map[string]any {
	fields := map[string]any{}
	if id == "asset-1" {
		fields["filenameEnc"] = b64("IMG_0001.HEIC")
		fields["resOriginalFileType"] = "public.heic"
		fields["resOriginalRes"] = resourceValue(base, id, "resOriginal")
		fields["resOriginalAltFileType"] = "public.jpeg"
		fields["resOriginalAltRes"] = resourceValue(base, id, "resOriginalAlt")
		fields["resOriginalVidComplFileType"] = "com.apple.quicktime-movie"
		fields["resOriginalVidComplRes"] = resourceValue(base, id, "resOriginalVidCompl")
	} else {
		fields["filenameEnc"] = b64("IMG_0002.JPG")
		fields["resOriginalFileType"] = "public.jpeg"
		fields["resOriginalRes"] = resourceValue(base, id, "resOriginal")
	}
	return ckRecord("CPLMaster", "master-"+id, fields)
}

func (l *icloudLibrary) albumRecord() map[string]any {
	return ckRecord("CPLAlbum", "album-trip", map[string]any{
		"albumNameEnc": b64("旅行"),
		"isDeleted":    false,
	})
}

func (l *icloudLibrary) relationRecord(asset string) map[string]any {
	return ckRecord("CPLContainerRelation", asset+"-IN-album-trip", map[string]any{
		"containerId": "album-trip",
		"itemId":      asset,
	})
}

func (l *icloudLibrary) writeAuth(w http.ResponseWriter, session bool) {
	w.Header().Set("scnt", "mock-scnt")
	w.Header().Set("X-Apple-ID-Session-Id", "mock-session")
	if session {
		w.Header().Set("X-Apple-Session-Token", "mock-session-token")
		w.Header().Set("X-Apple-ID-Account-Country", "US")
	}
}

func resourceValue(base, asset, variant string) map[string]any {
	content := icloudContent(asset, variant)
	return map[string]any{
		"fileChecksum": cloudKitChecksum(content),
		"size":         len(content),
		"downloadURL":  base + "/icloud/download/" + asset + "/" + variant,
	}
}

func icloudContent(asset, variant string) []byte {
	switch asset + "/" + variant {
	case "asset-1/resOriginal":
		return []byte("icloud-e2e-photo\n")
	case "asset-1/resOriginalAlt":
		return []byte("icloud-e2e-alt\n")
	case "asset-1/resOriginalVidCompl":
		return []byte("icloud-e2e-video\n")
	case "asset-2/resOriginal":
		return []byte("icloud-e2e-delta\n")
	default:
		return nil
	}
}

func cloudKitChecksum(content []byte) string {
	sum := sha1.Sum(content)
	return base64.StdEncoding.EncodeToString(append([]byte{1}, sum[:]...))
}

func b64(text string) string { return base64.StdEncoding.EncodeToString([]byte(text)) }

func ckRecord(recordType, name string, fields map[string]any) map[string]any {
	wrapped := make(map[string]any, len(fields))
	for key, value := range fields {
		wrapped[key] = map[string]any{"value": value}
	}
	return map[string]any{"recordType": recordType, "recordName": name, "fields": wrapped}
}

func zonePage(token string, more bool, records []any) map[string]any {
	if records == nil {
		records = []any{}
	}
	return map[string]any{"zones": []any{map[string]any{
		"zoneID":     map[string]any{"zoneName": "PrimarySync"},
		"syncToken":  token,
		"moreComing": more,
		"records":    records,
	}}}
}

func readJSONMap(r *http.Request) (map[string]any, error) {
	defer r.Body.Close()
	var body any
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		return nil, err
	}
	object, ok := body.(map[string]any)
	if !ok {
		return map[string]any{}, nil
	}
	return object, nil
}

func dig(value any, path ...string) any {
	current := value
	for _, key := range path {
		object, ok := current.(map[string]any)
		if !ok {
			return nil
		}
		current = object[key]
	}
	return current
}

func jsonInt(value any) int {
	switch typed := value.(type) {
	case float64:
		return int(typed)
	case int:
		return typed
	case json.Number:
		parsed, _ := typed.Int64()
		return int(parsed)
	default:
		return 0
	}
}

func queryRank(body map[string]any) int {
	filters, _ := dig(body, "query", "filterBy").([]any)
	for _, filter := range filters {
		if dig(filter, "fieldName") == "startRank" {
			return jsonInt(dig(filter, "fieldValue", "value"))
		}
	}
	return 0
}
