FROM --platform=$BUILDPLATFORM golang:1.25 AS build
ARG TARGETOS TARGETARCH VERSION=dev
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH go build -trimpath -ldflags="-s -w -X main.version=${VERSION}" -o /out/orchestrator ./cmd/orchestrator

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/orchestrator /orchestrator
USER 65532:65532
ENTRYPOINT ["/orchestrator"]
