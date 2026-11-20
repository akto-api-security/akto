// Only the last characters of the signature are shown: enough to tell tokens apart, too few to use.
export const maskedToken = (hint) => hint ? `******${hint}` : '-'
