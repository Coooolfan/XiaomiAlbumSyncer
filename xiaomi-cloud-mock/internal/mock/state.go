package mock

import (
	"encoding/json"
	"errors"
	"fmt"
	"sort"
	"strings"
	"sync"
)

var ErrRevisionConflict = errors.New("revision conflict")

type State struct {
	mu       sync.RWMutex
	scenario Scenario
	data     *runtimeData
	revision int64
	network  NetworkProfile
	stats    *Stats
}

func NewState(scenario Scenario) (*State, error) {
	data, err := buildRuntime(scenario)
	if err != nil {
		return nil, err
	}
	network := normalizeNetwork(scenario.Network)
	return &State{scenario: scenario, data: data, revision: 1, network: network, stats: NewStats()}, nil
}

func (s *State) Reset(seed *int64) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	scenario := s.scenario
	if seed != nil {
		scenario.Seed = *seed
	}
	data, err := buildRuntime(scenario)
	if err != nil {
		return err
	}
	s.scenario = scenario
	s.data = data
	s.network = normalizeNetwork(scenario.Network)
	s.revision++
	s.stats.Reset()
	return nil
}

func (s *State) Health() (int64, int64) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.scenario.Seed, s.revision
}

func (s *State) authenticate(userID, token string) (*Account, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	account := s.data.Accounts[userID]
	if account == nil || account.ServiceToken != token {
		return nil, false
	}
	// Published runtimeData is immutable. Mutations build and atomically publish a deep copy,
	// so handlers can safely keep this account snapshot after releasing the read lock.
	return account, true
}

func (s *State) login(userID, passToken string) (*Account, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	account := s.data.Accounts[userID]
	if account == nil || account.PassToken != passToken {
		return nil, false
	}
	return account, true
}

func (s *State) deleted(id int64) (deletedMedia, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	deleted, ok := s.data.Deleted[id]
	return deleted, ok
}

func (s *State) networkFor(id int64) NetworkProfile {
	s.mu.RLock()
	defer s.mu.RUnlock()
	profile := s.network
	profile.MediaOverrides = nil
	if override, ok := s.network.MediaOverrides[fmt.Sprintf("%d", id)]; ok {
		if override.ResponseDelayMs != 0 {
			profile.ResponseDelayMs = override.ResponseDelayMs
		}
		if override.ChunkSizeBytes != 0 {
			profile.ChunkSizeBytes = override.ChunkSizeBytes
		}
		if override.BytesPerSecond != 0 {
			profile.BytesPerSecond = override.BytesPerSecond
		}
	}
	return normalizeNetwork(profile)
}

func (s *State) UpdateNetwork(profile NetworkProfile) int64 {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.network = normalizeNetwork(profile)
	s.revision++
	return s.revision
}

func normalizeNetwork(profile NetworkProfile) NetworkProfile {
	if profile.ChunkSizeBytes <= 0 {
		profile.ChunkSizeBytes = 32768
	}
	if profile.ChunkSizeBytes > 1024*1024 {
		profile.ChunkSizeBytes = 1024 * 1024
	}
	if profile.ResponseDelayMs < 0 {
		profile.ResponseDelayMs = 0
	}
	if profile.BytesPerSecond < 0 {
		profile.BytesPerSecond = 0
	}
	return profile
}

func (s *State) Snapshot(includeMedia bool, mediaPage, mediaPageSize int) map[string]any {
	s.mu.RLock()
	defer s.mu.RUnlock()
	if mediaPage < 0 {
		mediaPage = 0
	}
	if mediaPageSize <= 0 || mediaPageSize > 1000 {
		mediaPageSize = 100
	}
	accounts := make([]map[string]any, 0, len(s.data.Accounts))
	userIDs := sortedAccountIDs(s.data.Accounts)
	for _, userID := range userIDs {
		account := s.data.Accounts[userID]
		albumIDs := make([]int64, 0, len(account.GalleryAlbums))
		for id := range account.GalleryAlbums {
			albumIDs = append(albumIDs, id)
		}
		sort.Slice(albumIDs, func(i, j int) bool { return albumIDs[i] < albumIDs[j] })
		albums := make([]map[string]any, 0, len(albumIDs))
		for _, albumID := range albumIDs {
			album := account.GalleryAlbums[albumID]
			entry := map[string]any{"albumId": album.AlbumID, "name": album.Name, "mediaCount": len(album.Assets), "lastUpdateTime": album.LastUpdateTime}
			if includeMedia {
				assets := sortedGalleryAssets(album.Assets)
				entry["assets"] = paginate(assets, mediaPage, mediaPageSize)
			}
			albums = append(albums, entry)
		}
		recordings := sortedRecordings(account.Recordings)
		accountEntry := map[string]any{"userId": userID, "galleryAlbums": albums, "recordingCount": len(recordings)}
		if includeMedia {
			accountEntry["recordings"] = paginate(recordings, mediaPage, mediaPageSize)
		}
		accounts = append(accounts, accountEntry)
	}
	return map[string]any{"seed": s.scenario.Seed, "revision": s.revision, "accounts": accounts}
}

func paginate[T any](items []T, page, pageSize int) []T {
	start := page * pageSize
	if start >= len(items) {
		return []T{}
	}
	end := min(start+pageSize, len(items))
	return items[start:end]
}

func (s *State) Mutate(request MutationRequest) (MutationResult, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if request.ExpectedRevision != nil && *request.ExpectedRevision != s.revision {
		return MutationResult{}, ErrRevisionConflict
	}
	if len(request.Operations) == 0 {
		return MutationResult{}, errors.New("operations cannot be empty")
	}
	copyData := cloneRuntime(s.data)
	affected := make([]int64, 0)
	for _, operation := range request.Operations {
		ids, err := applyMutation(copyData, s.scenario.Seed, operation)
		if err != nil {
			return MutationResult{}, err
		}
		affected = append(affected, ids...)
		copyData.Clock += 1000
	}
	s.data = copyData
	s.revision++
	return MutationResult{Revision: s.revision, AffectedIDs: affected}, nil
}

func applyMutation(data *runtimeData, seed int64, op MutationOperation) ([]int64, error) {
	account := data.Accounts[op.UserID]
	if account == nil {
		return nil, fmt.Errorf("unknown userId %q", op.UserID)
	}
	switch op.Op {
	case "createAlbum":
		if op.Album == nil {
			return nil, errors.New("createAlbum requires album")
		}
		if op.Album.AlbumID == -1 {
			return nil, errors.New("albumId -1 is local-only and cannot be created remotely")
		}
		if _, exists := account.GalleryAlbums[op.Album.AlbumID]; exists {
			return nil, fmt.Errorf("album %d already exists", op.Album.AlbumID)
		}
		account.GalleryAlbums[op.Album.AlbumID] = &GalleryAlbum{AlbumID: op.Album.AlbumID, Name: op.Album.Name, LastUpdateTime: data.Clock, Assets: map[int64]*GalleryAsset{}}
		return []int64{op.Album.AlbumID}, nil
	case "updateAlbum":
		album := account.GalleryAlbums[op.AlbumID]
		if album == nil || op.Album == nil {
			return nil, fmt.Errorf("album %d not found or album body missing", op.AlbumID)
		}
		if op.Album.Name != "" {
			album.Name = op.Album.Name
		}
		album.LastUpdateTime = data.Clock
		return []int64{op.AlbumID}, nil
	case "deleteAlbum":
		album := account.GalleryAlbums[op.AlbumID]
		if album == nil {
			return nil, fmt.Errorf("album %d not found", op.AlbumID)
		}
		ids := make([]int64, 0, len(album.Assets))
		for id := range album.Assets {
			data.Deleted[id] = deletedMedia{UserID: account.UserID, Kind: "gallery"}
			ids = append(ids, id)
		}
		delete(account.GalleryAlbums, op.AlbumID)
		return append([]int64{op.AlbumID}, ids...), nil
	case "addAssets":
		album := account.GalleryAlbums[op.AlbumID]
		if album == nil {
			return nil, fmt.Errorf("album %d not found", op.AlbumID)
		}
		specs, err := expandGallerySpecs(op)
		if err != nil {
			return nil, err
		}
		ids := make([]int64, 0, len(specs))
		for i, spec := range specs {
			id, err := allocateRuntimeID(data, spec.ID)
			if err != nil {
				return nil, err
			}
			if spec.DateTaken == 0 {
				spec.DateTaken = data.Clock + int64(i)*1000
			}
			asset, err := galleryAssetFromSpec(seed, id, spec, len(album.Assets))
			if err != nil {
				return nil, err
			}
			album.Assets[id] = asset
			album.LastUpdateTime = max(album.LastUpdateTime, data.Clock, asset.DateTaken)
			delete(data.Deleted, id)
			ids = append(ids, id)
		}
		return ids, nil
	case "updateAsset":
		album := account.GalleryAlbums[op.AlbumID]
		if album == nil || op.Asset == nil {
			return nil, fmt.Errorf("album %d not found or asset body missing", op.AlbumID)
		}
		current := album.Assets[op.Asset.ID]
		if current == nil {
			return nil, fmt.Errorf("asset %d not found", op.Asset.ID)
		}
		spec := mergeGallerySpec(current, *op.Asset)
		updated, err := galleryAssetFromSpec(seed, current.ID, spec, 0)
		if err != nil {
			return nil, err
		}
		updated.Version = current.Version + 1
		updated.SHA1 = resolveSHA1(seed, updated.ID, updated.Version, updated.Size, updated.ContentPattern, op.Asset.SHA1, op.Asset.SHA1Mode)
		album.Assets[updated.ID] = updated
		album.LastUpdateTime = data.Clock
		return []int64{updated.ID}, nil
	case "deleteAssets":
		album := account.GalleryAlbums[op.AlbumID]
		if album == nil {
			return nil, fmt.Errorf("album %d not found", op.AlbumID)
		}
		ids, err := selectGalleryIDs(album.Assets, op)
		if err != nil {
			return nil, err
		}
		for _, id := range ids {
			delete(album.Assets, id)
			data.Deleted[id] = deletedMedia{UserID: account.UserID, Kind: "gallery"}
		}
		album.LastUpdateTime = data.Clock
		return ids, nil
	case "addRecordings":
		specs, err := expandRecordingSpecs(op)
		if err != nil {
			return nil, err
		}
		ids := make([]int64, 0, len(specs))
		for i, spec := range specs {
			id, err := allocateRuntimeID(data, spec.ID)
			if err != nil {
				return nil, err
			}
			if spec.CreateTime == 0 {
				spec.CreateTime = data.Clock + int64(i)*1000
			}
			recording, err := recordingFromSpec(seed, id, spec, len(account.Recordings))
			if err != nil {
				return nil, err
			}
			account.Recordings[id] = recording
			delete(data.Deleted, id)
			ids = append(ids, id)
		}
		return ids, nil
	case "updateRecording":
		if op.Recording == nil {
			return nil, errors.New("updateRecording requires recording")
		}
		current := account.Recordings[op.Recording.ID]
		if current == nil {
			return nil, fmt.Errorf("recording %d not found", op.Recording.ID)
		}
		spec := mergeRecordingSpec(current, *op.Recording)
		updated, err := recordingFromSpec(seed, current.ID, spec, 0)
		if err != nil {
			return nil, err
		}
		updated.Version = current.Version + 1
		updated.SHA1 = resolveSHA1(seed, updated.ID, updated.Version, updated.Size, updated.ContentPattern, op.Recording.SHA1, op.Recording.SHA1Mode)
		account.Recordings[updated.ID] = updated
		return []int64{updated.ID}, nil
	case "deleteRecordings":
		ids, err := selectRecordingIDs(account.Recordings, op)
		if err != nil {
			return nil, err
		}
		for _, id := range ids {
			delete(account.Recordings, id)
			data.Deleted[id] = deletedMedia{UserID: account.UserID, Kind: "recording"}
		}
		return ids, nil
	default:
		return nil, fmt.Errorf("unknown mutation op %q", op.Op)
	}
}

func expandGallerySpecs(op MutationOperation) ([]GalleryAssetSpec, error) {
	if len(op.Assets) > 0 {
		return op.Assets, nil
	}
	if op.Count <= 0 || len(op.Template) == 0 {
		return nil, errors.New("addAssets requires assets or count + template")
	}
	var template GalleryAssetSpec
	if err := json.Unmarshal(op.Template, &template); err != nil {
		return nil, err
	}
	result := make([]GalleryAssetSpec, op.Count)
	for i := range result {
		result[i] = template
		result[i].ID = 0
		result[i].DateTaken += int64(i) * 1000
		if template.FileName != "" {
			result[i].FileName = numberedName(template.FileName, i+1)
		}
	}
	return result, nil
}

func expandRecordingSpecs(op MutationOperation) ([]RecordingSpec, error) {
	if len(op.Recordings) > 0 {
		return op.Recordings, nil
	}
	if op.Count <= 0 || len(op.Template) == 0 {
		return nil, errors.New("addRecordings requires recordings or count + template")
	}
	var template RecordingSpec
	if err := json.Unmarshal(op.Template, &template); err != nil {
		return nil, err
	}
	result := make([]RecordingSpec, op.Count)
	for i := range result {
		result[i] = template
		result[i].ID = 0
		result[i].CreateTime += int64(i) * 1000
		if template.FileName != "" {
			result[i].FileName = numberedName(template.FileName, i+1)
			result[i].RawName = ""
		}
	}
	return result, nil
}

func numberedName(name string, ordinal int) string {
	ext := extension(name)
	base := strings.TrimSuffix(name, "."+ext)
	if ext == "" {
		return fmt.Sprintf("%s_%06d", name, ordinal)
	}
	return fmt.Sprintf("%s_%06d.%s", base, ordinal, ext)
}

func allocateRuntimeID(data *runtimeData, requested int64) (int64, error) {
	if requested != 0 {
		if mediaIDExists(data, requested) {
			return 0, fmt.Errorf("media id %d already exists", requested)
		}
		if requested >= data.NextMediaID {
			data.NextMediaID = requested + 1
		}
		return requested, nil
	}
	for mediaIDExists(data, data.NextMediaID) {
		data.NextMediaID++
	}
	id := data.NextMediaID
	data.NextMediaID++
	return id, nil
}

func mediaIDExists(data *runtimeData, id int64) bool {
	if _, exists := data.Deleted[id]; exists {
		return true
	}
	for _, account := range data.Accounts {
		if _, exists := account.Recordings[id]; exists {
			return true
		}
		for _, album := range account.GalleryAlbums {
			if _, exists := album.Assets[id]; exists {
				return true
			}
		}
	}
	return false
}

func selectGalleryIDs(items map[int64]*GalleryAsset, op MutationOperation) ([]int64, error) {
	if len(op.IDs) > 0 {
		for _, id := range op.IDs {
			if items[id] == nil {
				return nil, fmt.Errorf("asset %d not found", id)
			}
		}
		return append([]int64(nil), op.IDs...), nil
	}
	if op.Count <= 0 || op.Count > len(items) {
		return nil, errors.New("deleteAssets requires valid ids or count")
	}
	assets := sortedGalleryAssets(items)
	if op.Selection == "oldest" {
		reverse(assets)
	} else if op.Selection != "" && op.Selection != "newest" {
		return nil, fmt.Errorf("unsupported selection %q", op.Selection)
	}
	ids := make([]int64, op.Count)
	for i := range ids {
		ids[i] = assets[i].ID
	}
	return ids, nil
}

func selectRecordingIDs(items map[int64]*Recording, op MutationOperation) ([]int64, error) {
	if len(op.IDs) > 0 {
		for _, id := range op.IDs {
			if items[id] == nil {
				return nil, fmt.Errorf("recording %d not found", id)
			}
		}
		return append([]int64(nil), op.IDs...), nil
	}
	if op.Count <= 0 || op.Count > len(items) {
		return nil, errors.New("deleteRecordings requires valid ids or count")
	}
	recordings := sortedRecordings(items)
	if op.Selection == "oldest" {
		reverse(recordings)
	} else if op.Selection != "" && op.Selection != "newest" {
		return nil, fmt.Errorf("unsupported selection %q", op.Selection)
	}
	ids := make([]int64, op.Count)
	for i := range ids {
		ids[i] = recordings[i].ID
	}
	return ids, nil
}

func mergeGallerySpec(current *GalleryAsset, update GalleryAssetSpec) GalleryAssetSpec {
	spec := GalleryAssetSpec{ID: current.ID, Type: current.Type, FileName: current.FileName, Title: current.Title, MimeType: current.MimeType, DateTaken: current.DateTaken, Size: current.Size, SHA1: current.SHA1, ContentPattern: current.ContentPattern}
	if update.Type != "" {
		spec.Type = update.Type
	}
	if update.FileName != "" {
		spec.FileName = update.FileName
	}
	if update.Title != "" {
		spec.Title = update.Title
	}
	if update.MimeType != "" {
		spec.MimeType = update.MimeType
	}
	if update.DateTaken != 0 {
		spec.DateTaken = update.DateTaken
	}
	if update.Size != 0 {
		spec.Size = update.Size
	}
	if update.SHA1 != "" {
		spec.SHA1 = update.SHA1
	}
	if update.ContentPattern != "" {
		spec.ContentPattern = update.ContentPattern
	}
	return spec
}

func mergeRecordingSpec(current *Recording, update RecordingSpec) RecordingSpec {
	spec := RecordingSpec{ID: current.ID, FileName: current.FileName, RawName: current.RawName, RecordingType: current.RecordingType, CreateTime: current.CreateTime, Size: current.Size, SHA1: current.SHA1, ContentPattern: current.ContentPattern}
	if update.FileName != "" {
		spec.FileName = update.FileName
	}
	if update.RawName != "" {
		spec.RawName = update.RawName
	}
	if update.RecordingType != 0 {
		spec.RecordingType = update.RecordingType
	}
	if update.CreateTime != 0 {
		spec.CreateTime = update.CreateTime
	}
	if update.Size != 0 {
		spec.Size = update.Size
	}
	if update.SHA1 != "" {
		spec.SHA1 = update.SHA1
	}
	if update.ContentPattern != "" {
		spec.ContentPattern = update.ContentPattern
	}
	return spec
}

func cloneRuntime(data *runtimeData) *runtimeData {
	copyData := &runtimeData{Accounts: map[string]*Account{}, Deleted: map[int64]deletedMedia{}, NextMediaID: data.NextMediaID, Clock: data.Clock}
	for id, deleted := range data.Deleted {
		copyData.Deleted[id] = deleted
	}
	for userID, account := range data.Accounts {
		copyData.Accounts[userID] = cloneAccount(account)
	}
	return copyData
}

func cloneAccount(account *Account) *Account {
	copyAccount := &Account{UserID: account.UserID, PassToken: account.PassToken, ServiceToken: account.ServiceToken, GalleryAlbums: map[int64]*GalleryAlbum{}, Recordings: map[int64]*Recording{}}
	for id, album := range account.GalleryAlbums {
		copyAlbum := &GalleryAlbum{AlbumID: album.AlbumID, Name: album.Name, LastUpdateTime: album.LastUpdateTime, Assets: map[int64]*GalleryAsset{}}
		for assetID, asset := range album.Assets {
			value := *asset
			copyAlbum.Assets[assetID] = &value
		}
		copyAccount.GalleryAlbums[id] = copyAlbum
	}
	for id, recording := range account.Recordings {
		value := *recording
		copyAccount.Recordings[id] = &value
	}
	return copyAccount
}

func sortedAccountIDs(accounts map[string]*Account) []string {
	result := make([]string, 0, len(accounts))
	for id := range accounts {
		result = append(result, id)
	}
	sort.Strings(result)
	return result
}

func sortedGalleryAssets(items map[int64]*GalleryAsset) []*GalleryAsset {
	result := make([]*GalleryAsset, 0, len(items))
	for _, item := range items {
		value := *item
		result = append(result, &value)
	}
	sort.Slice(result, func(i, j int) bool {
		if result[i].DateTaken == result[j].DateTaken {
			return result[i].ID > result[j].ID
		}
		return result[i].DateTaken > result[j].DateTaken
	})
	return result
}

func sortedRecordings(items map[int64]*Recording) []*Recording {
	result := make([]*Recording, 0, len(items))
	for _, item := range items {
		value := *item
		result = append(result, &value)
	}
	sort.Slice(result, func(i, j int) bool {
		if result[i].CreateTime == result[j].CreateTime {
			return result[i].ID > result[j].ID
		}
		return result[i].CreateTime > result[j].CreateTime
	})
	return result
}

func reverse[T any](values []T) {
	for i, j := 0, len(values)-1; i < j; i, j = i+1, j-1 {
		values[i], values[j] = values[j], values[i]
	}
}
