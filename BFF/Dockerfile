
# Build stage
FROM golang:1.26 AS build

WORKDIR /workspace

# Copy Go module files first for dependency caching
COPY BFF/go.mod BFF/go.sum ./

RUN go mod download

# Copy central protobuf definitions and BFF source
COPY protos/ /workspace/protos/
COPY BFF/ /workspace/

# Build the BFF executable
RUN CGO_ENABLED=0 GOOS=linux go build -o /app/bff ./cmd/bff

# Runtime stage
FROM gcr.io/distroless/static-debian13:nonroot

WORKDIR /app

COPY --from=build --chown=nonroot:nonroot /app/bff /app/bff

EXPOSE 8080

ENTRYPOINT ["/app/bff"]
