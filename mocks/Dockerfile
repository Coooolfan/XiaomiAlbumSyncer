FROM golang:1.25-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY cmd ./cmd
COPY internal ./internal
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/mocks ./cmd/mocks

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/mocks /mocks
COPY scenarios /scenarios
EXPOSE 18080
ENTRYPOINT ["/mocks", "--listen", "0.0.0.0:18080", "--scenario", "/scenarios/default.json"]
