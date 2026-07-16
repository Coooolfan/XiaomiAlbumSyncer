package mock

import (
	"context"
	"crypto/sha256"
	"fmt"
	"io"
	"time"
)

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
