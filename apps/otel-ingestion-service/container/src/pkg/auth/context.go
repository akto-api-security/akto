package auth

import "context"

type ctxKey int

const accountIDKey ctxKey = 1

func WithAccountID(ctx context.Context, accountID int) context.Context {
	return context.WithValue(ctx, accountIDKey, accountID)
}

func AccountIDFromContext(ctx context.Context) (int, bool) {
	v, ok := ctx.Value(accountIDKey).(int)
	if !ok || v <= 0 {
		return 0, false
	}
	return v, true
}
