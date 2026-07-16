package mock

import "sync"

type Stats struct {
	mu                      sync.Mutex
	RouteCounts             map[string]int64 `json:"routeCounts"`
	TimelineAlbumIDs        map[string]int64 `json:"timelineAlbumIds"`
	BytesSent               int64            `json:"bytesSent"`
	ActiveDownloads         int64            `json:"activeDownloads"`
	PeakConcurrentDownloads int64            `json:"peakConcurrentDownloads"`
	UnexpectedRequests      int64            `json:"unexpectedRequests"`
	Notifications           int64            `json:"notifications"`
}

func NewStats() *Stats {
	return &Stats{RouteCounts: map[string]int64{}, TimelineAlbumIDs: map[string]int64{}}
}

func (s *Stats) Reset() {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.RouteCounts = map[string]int64{}
	s.TimelineAlbumIDs = map[string]int64{}
	s.BytesSent = 0
	s.ActiveDownloads = 0
	s.PeakConcurrentDownloads = 0
	s.UnexpectedRequests = 0
	s.Notifications = 0
}

func (s *Stats) RecordRoute(path string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.RouteCounts[path]++
}

func (s *Stats) RecordTimeline(albumID string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.TimelineAlbumIDs[albumID]++
}

func (s *Stats) Unexpected() {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.UnexpectedRequests++
}

func (s *Stats) Notify() {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.Notifications++
}

func (s *Stats) BeginDownload() {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.ActiveDownloads++
	if s.ActiveDownloads > s.PeakConcurrentDownloads {
		s.PeakConcurrentDownloads = s.ActiveDownloads
	}
}

func (s *Stats) EndDownload(bytes int64) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.ActiveDownloads--
	s.BytesSent += bytes
}

func (s *Stats) Snapshot() map[string]any {
	s.mu.Lock()
	defer s.mu.Unlock()
	routes := make(map[string]int64, len(s.RouteCounts))
	for key, value := range s.RouteCounts {
		routes[key] = value
	}
	timelines := make(map[string]int64, len(s.TimelineAlbumIDs))
	for key, value := range s.TimelineAlbumIDs {
		timelines[key] = value
	}
	return map[string]any{
		"routeCounts":             routes,
		"timelineAlbumIds":        timelines,
		"bytesSent":               s.BytesSent,
		"activeDownloads":         s.ActiveDownloads,
		"peakConcurrentDownloads": s.PeakConcurrentDownloads,
		"unexpectedRequests":      s.UnexpectedRequests,
		"notifications":           s.Notifications,
	}
}
