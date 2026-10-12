package util

import (
	"crypto/md5"
	"encoding/hex"
	"fmt"
	"strconv"
)

// GenerateStockOperationID mirrors Order Service's
// StockOperationIdUtils.generateOperationId().
func GenerateStockOperationID(
	userID int64,
	idempotencyKey string,
	itemIndex int,
) string {
	source := strconv.FormatInt(userID, 10) + ":" +
		idempotencyKey + ":" +
		strconv.Itoa(itemIndex)

	hash := md5.Sum([]byte(source))

	// Match Java UUID.nameUUIDFromBytes(): UUID version 3,
	// with the RFC 4122 variant bits.
	hash[6] = (hash[6] & 0x0f) | 0x30
	hash[8] = (hash[8] & 0x3f) | 0x80

	encoded := hex.EncodeToString(hash[:])

	return fmt.Sprintf(
		"%s-%s-%s-%s-%s",
		encoded[0:8],
		encoded[8:12],
		encoded[12:16],
		encoded[16:20],
		encoded[20:32],
	)
}
