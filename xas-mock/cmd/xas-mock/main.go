package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/Coooolfan/XiaomiAlbumSyncer/xas-mock/internal/mock"
)

func main() {
	listen := flag.String("listen", "127.0.0.1:18080", "HTTP listen address")
	scenarioPath := flag.String("scenario", "", "scenario JSON path; built-in scenario is used when empty")
	publicURL := flag.String("public-url", "", "public base URL used in redirects and signed URLs")
	flag.Parse()

	scenario, err := mock.LoadScenario(*scenarioPath)
	if err != nil {
		log.Fatalf("load scenario: %v", err)
	}
	state, err := mock.NewState(scenario)
	if err != nil {
		log.Fatalf("initialize scenario: %v", err)
	}

	handler := mock.NewServer(state, *publicURL)
	server := &http.Server{
		Addr:              *listen,
		Handler:           handler,
		ReadHeaderTimeout: 10 * time.Second,
	}

	go func() {
		log.Printf("xas-mock listening on http://%s seed=%d", *listen, scenario.Seed)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("serve: %v", err)
		}
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)
	<-stop

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := server.Shutdown(ctx); err != nil {
		fmt.Fprintf(os.Stderr, "shutdown: %v\n", err)
	}
}
