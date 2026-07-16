package mock

import (
	"crypto/sha1"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"strings"
)

func LoadScenario(path string) (Scenario, error) {
	if path == "" {
		return DefaultScenario(), nil
	}
	f, err := os.Open(path)
	if err != nil {
		return Scenario{}, err
	}
	defer f.Close()
	var scenario Scenario
	decoder := json.NewDecoder(f)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&scenario); err != nil {
		return Scenario{}, err
	}
	return scenario, nil
}

func DefaultScenario() Scenario {
	const content = "xiaomi-album-syncer-api-e2e\n"
	return Scenario{
		Version:      1,
		Seed:         20260716,
		LogicalClock: 1714564800000,
		Network: NetworkProfile{
			ChunkSizeBytes: 32768,
		},
		Accounts: []AccountSpec{{
			UserID:    "mock-user",
			PassToken: "mock-pass-token",
			GalleryAlbums: []GalleryAlbumSpec{
				{
					AlbumID: 1,
					Name:    "Camera",
					Assets: []GalleryAssetSpec{{
						ID: 101, Type: "image", FileName: "sample-photo.jpg", Title: "sample-photo",
						MimeType: "image/jpeg", DateTaken: 1714564800000, Size: int64(len(content)),
						SHA1Mode: "exact", ContentPattern: content,
					}},
				},
				{AlbumID: 2, Name: "Screenshots"},
			},
			Recordings: []RecordingSpec{{
				ID: 201, FileName: "sample-audio.m4a", RecordingType: 0,
				CreateTime: 1714564800000, Size: int64(len(content)), SHA1Mode: "exact", ContentPattern: content,
			}},
		}},
	}
}

func buildRuntime(s Scenario) (*runtimeData, error) {
	if s.Version != 1 {
		return nil, fmt.Errorf("unsupported scenario version %d", s.Version)
	}
	if len(s.Accounts) == 0 {
		return nil, errors.New("scenario must contain at least one account")
	}
	data := &runtimeData{
		Accounts: map[string]*Account{},
		Deleted:  map[int64]deletedMedia{},
		Clock:    s.LogicalClock,
	}
	if data.Clock == 0 {
		data.Clock = 1714564800000
	}
	usedIDs := map[int64]struct{}{}
	var maxID int64
	for _, accountSpec := range s.Accounts {
		if accountSpec.UserID == "" || accountSpec.PassToken == "" {
			return nil, errors.New("account userId and passToken are required")
		}
		if _, exists := data.Accounts[accountSpec.UserID]; exists {
			return nil, fmt.Errorf("duplicate userId %q", accountSpec.UserID)
		}
		account := &Account{
			UserID: accountSpec.UserID, PassToken: accountSpec.PassToken,
			ServiceToken:  serviceToken(s.Seed, accountSpec.UserID),
			GalleryAlbums: map[int64]*GalleryAlbum{}, Recordings: map[int64]*Recording{},
		}
		data.Accounts[account.UserID] = account
		for _, albumSpec := range accountSpec.GalleryAlbums {
			if albumSpec.AlbumID == -1 {
				return nil, errors.New("albumId -1 is reserved for XiaomiAlbumSyncer local recording normalization")
			}
			if _, exists := account.GalleryAlbums[albumSpec.AlbumID]; exists {
				return nil, fmt.Errorf("duplicate albumId %d for %s", albumSpec.AlbumID, account.UserID)
			}
			album := &GalleryAlbum{AlbumID: albumSpec.AlbumID, Name: albumSpec.Name, LastUpdateTime: albumSpec.LastUpdateTime, Assets: map[int64]*GalleryAsset{}}
			if album.Name == "" {
				album.Name = fmt.Sprintf("Album %d", album.AlbumID)
			}
			account.GalleryAlbums[album.AlbumID] = album
			assets := append([]GalleryAssetSpec(nil), albumSpec.Assets...)
			if albumSpec.Generate != nil {
				assets = append(assets, generateGallerySpecs(*albumSpec.Generate)...)
			}
			for _, spec := range assets {
				id, err := assignID(spec.ID, &maxID, usedIDs)
				if err != nil {
					return nil, err
				}
				asset, err := galleryAssetFromSpec(s.Seed, id, spec, len(album.Assets))
				if err != nil {
					return nil, fmt.Errorf("album %d asset %d: %w", album.AlbumID, id, err)
				}
				album.Assets[id] = asset
				if asset.DateTaken > album.LastUpdateTime {
					album.LastUpdateTime = asset.DateTaken
				}
				if asset.DateTaken > data.Clock {
					data.Clock = asset.DateTaken
				}
			}
		}
		for _, spec := range accountSpec.Recordings {
			id, err := assignID(spec.ID, &maxID, usedIDs)
			if err != nil {
				return nil, err
			}
			recording, err := recordingFromSpec(s.Seed, id, spec, len(account.Recordings))
			if err != nil {
				return nil, fmt.Errorf("recording %d: %w", id, err)
			}
			account.Recordings[id] = recording
			if recording.CreateTime > data.Clock {
				data.Clock = recording.CreateTime
			}
		}
	}
	data.NextMediaID = maxID + 1
	if data.NextMediaID <= 0 {
		data.NextMediaID = 1
	}
	return data, nil
}

func assignID(requested int64, maxID *int64, used map[int64]struct{}) (int64, error) {
	id := requested
	if id == 0 {
		id = *maxID + 1
		for {
			if _, exists := used[id]; !exists {
				break
			}
			id++
		}
	}
	if _, exists := used[id]; exists {
		return 0, fmt.Errorf("duplicate media id %d", id)
	}
	used[id] = struct{}{}
	if id > *maxID {
		*maxID = id
	}
	return id, nil
}

func generateGallerySpecs(g GeneratorSpec) []GalleryAssetSpec {
	result := make([]GalleryAssetSpec, 0, max(g.Count, 0))
	types := g.TypeCycle
	if len(types) == 0 {
		types = []string{"image", "video"}
	}
	interval := g.IntervalSeconds
	if interval == 0 {
		interval = 60
	}
	for i := 0; i < g.Count; i++ {
		result = append(result, GalleryAssetSpec{
			Type: types[i%len(types)], Size: g.SizeBytes,
			DateTaken: g.StartTime + int64(i)*interval*1000,
			FileName:  fmt.Sprintf("%s%06d", g.NamePrefix, i+1),
		})
	}
	return result
}

func galleryAssetFromSpec(seed, id int64, spec GalleryAssetSpec, ordinal int) (*GalleryAsset, error) {
	typeName := strings.ToLower(spec.Type)
	if typeName == "" {
		typeName = "image"
	}
	if typeName != "image" && typeName != "video" {
		return nil, fmt.Errorf("gallery asset type must be image or video, got %q", typeName)
	}
	if spec.Size < 0 {
		return nil, errors.New("size cannot be negative")
	}
	fileName := spec.FileName
	if fileName == "" {
		if typeName == "image" {
			fileName = fmt.Sprintf("IMG_%06d.jpg", ordinal+1)
		} else {
			fileName = fmt.Sprintf("VID_%06d.mp4", ordinal+1)
		}
	} else if !strings.Contains(fileName, ".") {
		if typeName == "image" {
			fileName += ".jpg"
		} else {
			fileName += ".mp4"
		}
	}
	mimeType := spec.MimeType
	if mimeType == "" {
		if typeName == "image" {
			mimeType = "image/jpeg"
		} else {
			mimeType = "video/mp4"
		}
	}
	title := spec.Title
	if title == "" {
		title = strings.TrimSuffix(fileName, "."+extension(fileName))
	}
	asset := &GalleryAsset{ID: id, Type: typeName, FileName: fileName, Title: title, MimeType: mimeType, DateTaken: spec.DateTaken, Size: spec.Size, ContentPattern: spec.ContentPattern, Version: 1}
	asset.SHA1 = resolveSHA1(seed, id, asset.Version, asset.Size, asset.ContentPattern, spec.SHA1, spec.SHA1Mode)
	return asset, nil
}

func recordingFromSpec(seed, id int64, spec RecordingSpec, ordinal int) (*Recording, error) {
	if spec.Size < 0 {
		return nil, errors.New("size cannot be negative")
	}
	fileName := spec.FileName
	if fileName == "" {
		fileName = fmt.Sprintf("REC_%06d.m4a", ordinal+1)
	}
	rawName := spec.RawName
	if rawName == "" {
		rawName = fmt.Sprintf("%s_%d_%d_%d_%d", fileName, 4054, spec.RecordingType, id, spec.CreateTime)
	}
	recording := &Recording{ID: id, FileName: fileName, RawName: rawName, RecordingType: spec.RecordingType, CreateTime: spec.CreateTime, Size: spec.Size, ContentPattern: spec.ContentPattern, Version: 1}
	recording.SHA1 = resolveSHA1(seed, id, recording.Version, recording.Size, recording.ContentPattern, spec.SHA1, spec.SHA1Mode)
	return recording, nil
}

func resolveSHA1(seed, id, version, size int64, pattern, declared, mode string) string {
	if declared != "" {
		return declared
	}
	if mode == "exact" {
		h := sha1.New()
		_ = writePattern(h, seed, id, version, size, pattern, 32768, 0)
		return hex.EncodeToString(h.Sum(nil))
	}
	sum := sha1.Sum([]byte(fmt.Sprintf("declared:%d:%d:%d", seed, id, version)))
	return hex.EncodeToString(sum[:])
}

func extension(name string) string {
	idx := strings.LastIndexByte(name, '.')
	if idx < 0 || idx == len(name)-1 {
		return ""
	}
	return name[idx+1:]
}

func serviceToken(seed int64, userID string) string {
	sum := sha1.Sum([]byte(fmt.Sprintf("service-token:%d:%s", seed, userID)))
	return "mock-" + hex.EncodeToString(sum[:12])
}

func writePattern(w io.Writer, seed, id, version, size int64, literal string, chunkSize int, bytesPerSecond int64) error {
	return streamPattern(w, seed, id, version, size, literal, chunkSize, bytesPerSecond, nil)
}
