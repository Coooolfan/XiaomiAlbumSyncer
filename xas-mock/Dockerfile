FROM golang:1.25-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY cmd ./cmd
COPY internal ./internal
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/xas-mock ./cmd/xas-mock

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/xas-mock /xas-mock
COPY scenarios /scenarios
EXPOSE 18080
ENTRYPOINT ["/xas-mock", "--listen", "0.0.0.0:18080", "--scenario", "/scenarios/default.json"]
