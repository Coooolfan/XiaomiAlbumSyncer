package mock

import "encoding/json"

type Scenario struct {
	Version      int            `json:"version"`
	Seed         int64          `json:"seed"`
	LogicalClock int64          `json:"logicalClock"`
	Network      NetworkProfile `json:"network"`
	Accounts     []AccountSpec  `json:"accounts"`
}

type AccountSpec struct {
	UserID        string             `json:"userId"`
	PassToken     string             `json:"passToken"`
	GalleryAlbums []GalleryAlbumSpec `json:"galleryAlbums"`
	Recordings    []RecordingSpec    `json:"recordings"`
}

type GalleryAlbumSpec struct {
	AlbumID        int64              `json:"albumId"`
	Name           string             `json:"name"`
	LastUpdateTime int64              `json:"lastUpdateTime,omitempty"`
	Assets         []GalleryAssetSpec `json:"assets,omitempty"`
	Generate       *GeneratorSpec     `json:"generate,omitempty"`
}

type GalleryAssetSpec struct {
	ID             int64  `json:"id,omitempty"`
	Type           string `json:"type,omitempty"`
	FileName       string `json:"fileName,omitempty"`
	Title          string `json:"title,omitempty"`
	MimeType       string `json:"mimeType,omitempty"`
	DateTaken      int64  `json:"dateTaken,omitempty"`
	Size           int64  `json:"size,omitempty"`
	SHA1           string `json:"sha1,omitempty"`
	SHA1Mode       string `json:"sha1Mode,omitempty"`
	ContentPattern string `json:"contentPattern,omitempty"`
}

type RecordingSpec struct {
	ID             int64  `json:"id,omitempty"`
	FileName       string `json:"fileName,omitempty"`
	RawName        string `json:"rawName,omitempty"`
	RecordingType  int    `json:"recordingType,omitempty"`
	CreateTime     int64  `json:"createTime,omitempty"`
	Size           int64  `json:"size,omitempty"`
	SHA1           string `json:"sha1,omitempty"`
	SHA1Mode       string `json:"sha1Mode,omitempty"`
	ContentPattern string `json:"contentPattern,omitempty"`
}

type GeneratorSpec struct {
	Count           int      `json:"count"`
	TypeCycle       []string `json:"typeCycle,omitempty"`
	SizeBytes       int64    `json:"sizeBytes"`
	StartTime       int64    `json:"startTime"`
	IntervalSeconds int64    `json:"intervalSeconds"`
	NamePrefix      string   `json:"namePrefix,omitempty"`
	RecordingType   int      `json:"recordingType,omitempty"`
}

type NetworkProfile struct {
	ResponseDelayMs int64                     `json:"responseDelayMs"`
	ChunkSizeBytes  int                       `json:"chunkSizeBytes"`
	BytesPerSecond  int64                     `json:"bytesPerSecondPerDownload"`
	MediaOverrides  map[string]NetworkProfile `json:"mediaOverrides,omitempty"`
}

type MutationRequest struct {
	ExpectedRevision *int64              `json:"expectedRevision,omitempty"`
	Operations       []MutationOperation `json:"operations"`
}

type MutationOperation struct {
	Op         string             `json:"op"`
	UserID     string             `json:"userId"`
	AlbumID    int64              `json:"albumId,omitempty"`
	Album      *GalleryAlbumSpec  `json:"album,omitempty"`
	Asset      *GalleryAssetSpec  `json:"asset,omitempty"`
	Assets     []GalleryAssetSpec `json:"assets,omitempty"`
	Recording  *RecordingSpec     `json:"recording,omitempty"`
	Recordings []RecordingSpec    `json:"recordings,omitempty"`
	Count      int                `json:"count,omitempty"`
	Template   json.RawMessage    `json:"template,omitempty"`
	IDs        []int64            `json:"ids,omitempty"`
	Selection  string             `json:"selection,omitempty"`
}

type MutationResult struct {
	Revision    int64   `json:"revision"`
	AffectedIDs []int64 `json:"affectedIds"`
}

type GalleryAsset struct {
	ID             int64
	Type           string
	FileName       string
	Title          string
	MimeType       string
	DateTaken      int64
	Size           int64
	SHA1           string
	ContentPattern string
	Version        int64
}

type Recording struct {
	ID             int64
	FileName       string
	RawName        string
	RecordingType  int
	CreateTime     int64
	Size           int64
	SHA1           string
	ContentPattern string
	Version        int64
}

type GalleryAlbum struct {
	AlbumID        int64
	Name           string
	LastUpdateTime int64
	Assets         map[int64]*GalleryAsset
}

type Account struct {
	UserID        string
	PassToken     string
	ServiceToken  string
	GalleryAlbums map[int64]*GalleryAlbum
	Recordings    map[int64]*Recording
}

type deletedMedia struct {
	UserID string
	Kind   string
}

type runtimeData struct {
	Accounts    map[string]*Account
	Deleted     map[int64]deletedMedia
	NextMediaID int64
	Clock       int64
}
