package mock

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestICloudLoginSyncDownloadAndCursorExpiry(t *testing.T) {
	state, err := NewState(DefaultScenario())
	if err != nil {
		t.Fatal(err)
	}
	server := httptest.NewServer(NewServer(state, ""))
	defer server.Close()

	initResponse := icloudJSON(t, server, http.MethodPost, "/appleauth/auth/signin/init", map[string]any{"accountName": "mock-apple@example.com"})
	if initResponse.status != http.StatusOK || initResponse.body["protocol"] != "s2k" || initResponse.header.Get("scnt") == "" {
		t.Fatalf("signin init status=%d body=%v", initResponse.status, initResponse.body)
	}
	complete := icloudJSON(t, server, http.MethodPost, "/appleauth/auth/signin/complete?isRememberMeEnabled=true", map[string]any{"m1": "ignored"})
	if complete.status != http.StatusConflict {
		t.Fatalf("untrusted signin status=%d", complete.status)
	}
	rejected := icloudJSON(t, server, http.MethodPost, "/appleauth/auth/verify/trusteddevice/securitycode", map[string]any{"securityCode": map[string]any{"code": "000000"}})
	if rejected.status != http.StatusUnauthorized {
		t.Fatalf("wrong code status=%d", rejected.status)
	}
	icloudJSON(t, server, http.MethodPut, "/appleauth/auth/verify/trusteddevice/securitycode", nil)
	accepted := icloudJSON(t, server, http.MethodPost, "/appleauth/auth/verify/trusteddevice/securitycode", map[string]any{"securityCode": map[string]any{"code": icloudMFACode}})
	if accepted.status != http.StatusOK {
		t.Fatalf("verify status=%d", accepted.status)
	}
	trust := icloudJSON(t, server, http.MethodGet, "/appleauth/auth/2sv/trust", nil)
	if trust.header.Get("X-Apple-TwoSV-Trust-Token") != "mock-trust-token" {
		t.Fatalf("trust header=%q", trust.header.Get("X-Apple-TwoSV-Trust-Token"))
	}
	account := icloudJSON(t, server, http.MethodPost, "/setup/ws/1/accountLogin", map[string]any{})
	if account.body["hsaTrustedBrowser"] != true || account.body["domainToUse"] != "" {
		t.Fatalf("account=%v", account.body)
	}
	if account.header.Get("Set-Cookie") == "" || account.header.Get("X-Apple-Session-Token") == "" {
		t.Fatal("account login did not establish a session")
	}

	zones := icloudJSON(t, server, http.MethodPost, "/database/1/com.apple.photos.cloud/production/private/zones/list", map[string]any{})
	if len(zones.body["zones"].([]any)) != 1 {
		t.Fatalf("zones=%v", zones.body)
	}
	index := icloudJSON(t, server, http.MethodPost, "/database/1/com.apple.photos.cloud/production/private/records/query", map[string]any{
		"query": map[string]any{"recordType": "CheckIndexingState"},
	})
	if dig(index.body, "records") == nil {
		t.Fatalf("index=%v", index.body)
	}
	assets := icloudJSON(t, server, http.MethodPost, "/database/1/com.apple.photos.cloud/production/private/records/query", map[string]any{
		"resultsLimit": 200,
		"query": map[string]any{"recordType": "CPLAssetAndMasterByAssetDateWithoutHiddenOrDeleted", "filterBy": []any{
			map[string]any{"fieldName": "startRank", "fieldValue": map[string]any{"value": 0}},
		}},
	})
	records, _ := assets.body["records"].([]any)
	if len(records) != 2 {
		t.Fatalf("asset page=%v", assets.body)
	}
	photo := icloudBytes(t, server, "/icloud/download/asset-1/resOriginal")
	if string(photo) != "icloud-e2e-photo\n" {
		t.Fatalf("photo=%q", photo)
	}

	first := icloudJSON(t, server, http.MethodPost, "/database/1/com.apple.photos.cloud/production/private/changes/zone", map[string]any{
		"zones": []any{map[string]any{"syncToken": "base-1"}},
	})
	if dig(first.body, "zones") == nil || first.body["serverErrorCode"] != nil {
		t.Fatalf("first change=%v", first.body)
	}
	lookup := icloudJSON(t, server, http.MethodPost, "/database/1/com.apple.photos.cloud/production/private/records/lookup", map[string]any{
		"records": []any{map[string]any{"recordName": "master-asset-2"}, map[string]any{"recordName": "missing"}},
	})
	lookedUp, _ := lookup.body["records"].([]any)
	if len(lookedUp) != 2 || dig(lookedUp[1], "serverErrorCode") != "NOT_FOUND" {
		t.Fatalf("lookup=%v", lookup.body)
	}
	icloudJSON(t, server, http.MethodPost, "/database/1/com.apple.photos.cloud/production/private/changes/zone", map[string]any{
		"zones": []any{map[string]any{"syncToken": "page-2"}},
	})
	expired := icloudJSON(t, server, http.MethodPost, "/database/1/com.apple.photos.cloud/production/private/changes/zone", map[string]any{
		"zones": []any{map[string]any{"syncToken": "caught-up"}},
	})
	if expired.body["serverErrorCode"] != "CHANGE_TOKEN_EXPIRED" {
		t.Fatalf("expired=%v", expired.body)
	}
	recovered := icloudJSON(t, server, http.MethodPost, "/database/1/com.apple.photos.cloud/production/private/changes/zone", map[string]any{
		"zones": []any{map[string]any{"syncToken": "caught-up"}},
	})
	if recovered.body["serverErrorCode"] != nil {
		t.Fatalf("recovered=%v", recovered.body)
	}

	reset := icloudJSON(t, server, http.MethodPost, "/_control/v1/reset", map[string]any{})
	if reset.status != http.StatusOK {
		t.Fatalf("reset status=%d", reset.status)
	}
	afterReset := icloudJSON(t, server, http.MethodPost, "/appleauth/auth/signin/complete", map[string]any{})
	if afterReset.status != http.StatusConflict {
		t.Fatalf("reset did not clear trust, status=%d", afterReset.status)
	}
}

func TestICloudValidateRequiresSessionCookie(t *testing.T) {
	state, err := NewState(DefaultScenario())
	if err != nil {
		t.Fatal(err)
	}
	server := httptest.NewServer(NewServer(state, ""))
	defer server.Close()

	accepted := icloudJSON(t, server, http.MethodPost, "/appleauth/auth/verify/trusteddevice/securitycode", map[string]any{"securityCode": map[string]any{"code": icloudMFACode}})
	if accepted.status != http.StatusOK {
		t.Fatalf("verify status=%d", accepted.status)
	}
	trust := icloudJSON(t, server, http.MethodGet, "/appleauth/auth/2sv/trust", nil)
	if trust.status != http.StatusOK {
		t.Fatalf("trust status=%d", trust.status)
	}
	account := icloudJSON(t, server, http.MethodPost, "/setup/ws/1/accountLogin", map[string]any{})
	if account.status != http.StatusOK {
		t.Fatalf("accountLogin status=%d", account.status)
	}
	session := (&http.Response{Header: account.header}).Cookies()

	cases := []struct {
		name    string
		cookies []*http.Cookie
		status  int
	}{
		{"missing session cookie", nil, http.StatusUnauthorized},
		{"wrong session cookie", []*http.Cookie{{Name: "auth", Value: "wrong-cookie"}}, http.StatusUnauthorized},
		{"valid session cookie", session, http.StatusOK},
	}
	for _, tc := range cases {
		response := icloudJSON(t, server, http.MethodPost, "/setup/ws/1/validate", map[string]any{}, tc.cookies...)
		if response.status != tc.status {
			t.Fatalf("%s: validate status=%d body=%v", tc.name, response.status, response.body)
		}
		if response.header.Get("Set-Cookie") != "" || response.header.Get("X-Apple-Session-Token") != "" || response.header.Get("X-Apple-TwoSV-Trust-Token") != "" {
			t.Fatalf("%s: validate issued credentials, header=%v", tc.name, response.header)
		}
		if tc.status != http.StatusOK {
			continue
		}
		if response.body["hsaTrustedBrowser"] != true {
			t.Fatalf("%s: hsaTrustedBrowser body=%v", tc.name, response.body)
		}
		if dig(response.body, "webservices", "ckdatabasews", "url") != server.URL {
			t.Fatalf("%s: ckdatabasews body=%v", tc.name, response.body)
		}
	}
}

type icloudResponse struct {
	status int
	header http.Header
	body   map[string]any
}

func icloudJSON(t *testing.T, server *httptest.Server, method, path string, body any, cookies ...*http.Cookie) icloudResponse {
	t.Helper()
	var reader io.Reader
	if body != nil {
		encoded, err := json.Marshal(body)
		if err != nil {
			t.Fatal(err)
		}
		reader = bytes.NewReader(encoded)
	}
	request, err := http.NewRequest(method, server.URL+path, reader)
	if err != nil {
		t.Fatal(err)
	}
	for _, cookie := range cookies {
		request.AddCookie(cookie)
	}
	response, err := server.Client().Do(request)
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	var decoded map[string]any
	if err := json.NewDecoder(response.Body).Decode(&decoded); err != nil {
		t.Fatal(err)
	}
	return icloudResponse{status: response.StatusCode, header: response.Header, body: decoded}
}

func icloudBytes(t *testing.T, server *httptest.Server, path string) []byte {
	t.Helper()
	response, err := server.Client().Get(server.URL + path)
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		t.Fatalf("download status=%d", response.StatusCode)
	}
	content, err := io.ReadAll(response.Body)
	if err != nil {
		t.Fatal(err)
	}
	return content
}
