package mock

import (
	"context"
	"crypto/sha256"
	"fmt"
	"io"
	"time"
)

const jpegAPP15MaxPayload = 65533

var baseJPEG = []byte{
	0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01,
	0x02, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0xff, 0xfe, 0x00, 0x10,
	0x4c, 0x61, 0x76, 0x63, 0x36, 0x32, 0x2e, 0x32, 0x38, 0x2e, 0x31, 0x30,
	0x30, 0x00, 0xff, 0xdb, 0x00, 0x43, 0x00, 0x08, 0x04, 0x04, 0x04, 0x04,
	0x04, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x06, 0x06, 0x06, 0x06, 0x06,
	0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x07, 0x07, 0x07, 0x08,
	0x08, 0x08, 0x07, 0x07, 0x07, 0x06, 0x06, 0x07, 0x07, 0x08, 0x08, 0x08,
	0x08, 0x09, 0x09, 0x09, 0x08, 0x08, 0x08, 0x08, 0x09, 0x09, 0x0a, 0x0a,
	0x0a, 0x0c, 0x0c, 0x0b, 0x0b, 0x0e, 0x0e, 0x0e, 0x11, 0x11, 0x14, 0xff,
	0xc4, 0x00, 0x4b, 0x00, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
	0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x04, 0x01, 0x01,
	0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
	0x00, 0x00, 0x00, 0x06, 0x10, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
	0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x11, 0x01,
	0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
	0x00, 0x00, 0x00, 0x00, 0xff, 0xc0, 0x00, 0x11, 0x08, 0x00, 0x01, 0x00,
	0x01, 0x03, 0x01, 0x22, 0x00, 0x02, 0x11, 0x00, 0x03, 0x11, 0x00, 0xff,
	0xda, 0x00, 0x0c, 0x03, 0x01, 0x00, 0x02, 0x11, 0x03, 0x11, 0x00, 0x3f,
	0x00, 0x8c, 0x02, 0xb1, 0x97, 0xff, 0xd9,
}

func streamContent(w io.Writer, seed, id, version, size int64, literal, mode string, chunkSize int, bytesPerSecond int64, ctx context.Context) error {
	switch mode {
	case "":
		return streamPattern(w, seed, id, version, size, literal, chunkSize, bytesPerSecond, ctx)
	case "jpeg":
		return streamJPEG(w, seed, id, version, size, chunkSize, bytesPerSecond, ctx)
	default:
		return fmt.Errorf("unsupported content mode %q", mode)
	}
}

func streamPattern(w io.Writer, seed, id, version, size int64, literal string, chunkSize int, bytesPerSecond int64, ctx context.Context) error {
	if size == 0 {
		return nil
	}
	if chunkSize <= 0 {
		chunkSize = 32768
	}
	pattern := []byte(literal)
	if len(pattern) == 0 {
		block := make([]byte, 32768)
		base := sha256.Sum256([]byte(fmt.Sprintf("content:%d:%d:%d", seed, id, version)))
		for i := range block {
			block[i] = base[i%len(base)] ^ byte((i/len(base))%251)
		}
		pattern = block
	}
	buffer := make([]byte, chunkSize)
	started := time.Now()
	var written int64
	for written < size {
		if ctx != nil {
			select {
			case <-ctx.Done():
				return ctx.Err()
			default:
			}
		}
		remaining := size - written
		current := len(buffer)
		if remaining < int64(current) {
			current = int(remaining)
		}
		for i := 0; i < current; i++ {
			buffer[i] = pattern[(written+int64(i))%int64(len(pattern))]
		}
		n, err := w.Write(buffer[:current])
		written += int64(n)
		if err != nil {
			return err
		}
		if n != current {
			return io.ErrShortWrite
		}
		if bytesPerSecond > 0 {
			wholeSeconds := written / bytesPerSecond
			remainderBytes := written % bytesPerSecond
			targetElapsed := time.Duration(wholeSeconds)*time.Second +
				time.Duration(remainderBytes)*time.Second/time.Duration(bytesPerSecond)
			if delay := targetElapsed - time.Since(started); delay > 0 {
				timer := time.NewTimer(delay)
				if ctx == nil {
					<-timer.C
				} else {
					select {
					case <-timer.C:
					case <-ctx.Done():
						timer.Stop()
						return ctx.Err()
					}
				}
			}
		}
	}
	return nil
}

func streamJPEG(w io.Writer, seed, id, version, size int64, chunkSize int, bytesPerSecond int64, ctx context.Context) error {
	if size < int64(len(baseJPEG))+4 {
		return fmt.Errorf("jpeg content requires at least %d bytes", len(baseJPEG)+4)
	}
	writer := newPacedWriter(w, chunkSize, bytesPerSecond, ctx)
	if err := writer.write(baseJPEG[:2]); err != nil {
		return err
	}

	remaining := size - int64(len(baseJPEG))
	seedBlock := sha256.Sum256([]byte(fmt.Sprintf("jpeg-padding:%d:%d:%d", seed, id, version)))
	payload := make([]byte, min(jpegAPP15MaxPayload, max(int(remaining-4), 1)))
	for i := range payload {
		payload[i] = seedBlock[i%len(seedBlock)] ^ byte((i/len(seedBlock))%251)
	}
	for remaining > 0 {
		segmentSize := min(remaining, int64(jpegAPP15MaxPayload+4))
		if remaining-segmentSize > 0 && remaining-segmentSize < 4 {
			segmentSize -= 4 - (remaining - segmentSize)
		}
		payloadSize := int(segmentSize - 4)
		header := [4]byte{0xff, 0xef}
		header[2] = byte((payloadSize + 2) >> 8)
		header[3] = byte(payloadSize + 2)
		if err := writer.write(header[:]); err != nil {
			return err
		}
		if err := writer.write(payload[:payloadSize]); err != nil {
			return err
		}
		remaining -= segmentSize
	}
	return writer.write(baseJPEG[2:])
}

type pacedWriter struct {
	writer         io.Writer
	chunkSize      int
	bytesPerSecond int64
	ctx            context.Context
	started        time.Time
	written        int64
}

func newPacedWriter(w io.Writer, chunkSize int, bytesPerSecond int64, ctx context.Context) *pacedWriter {
	if chunkSize <= 0 {
		chunkSize = 32768
	}
	return &pacedWriter{writer: w, chunkSize: chunkSize, bytesPerSecond: bytesPerSecond, ctx: ctx, started: time.Now()}
}

func (w *pacedWriter) write(data []byte) error {
	for len(data) > 0 {
		if w.ctx != nil {
			select {
			case <-w.ctx.Done():
				return w.ctx.Err()
			default:
			}
		}
		current := min(len(data), w.chunkSize)
		n, err := w.writer.Write(data[:current])
		w.written += int64(n)
		if err != nil {
			return err
		}
		if n != current {
			return io.ErrShortWrite
		}
		data = data[current:]
		if err := w.throttle(); err != nil {
			return err
		}
	}
	return nil
}

func (w *pacedWriter) throttle() error {
	if w.bytesPerSecond <= 0 {
		return nil
	}
	wholeSeconds := w.written / w.bytesPerSecond
	remainderBytes := w.written % w.bytesPerSecond
	targetElapsed := time.Duration(wholeSeconds)*time.Second +
		time.Duration(remainderBytes)*time.Second/time.Duration(w.bytesPerSecond)
	delay := targetElapsed - time.Since(w.started)
	if delay <= 0 {
		return nil
	}
	timer := time.NewTimer(delay)
	if w.ctx == nil {
		<-timer.C
		return nil
	}
	select {
	case <-timer.C:
		return nil
	case <-w.ctx.Done():
		timer.Stop()
		return w.ctx.Err()
	}
}
