import { Box, Scrollable, Text, VerticalStack } from "@shopify/polaris";
import func from "@/util/func";
import { tokens } from "@shopify/polaris-tokens"

/** Polaris tokens + layout; Text would override body font, so log lines use Box + plain text. */
const logLineStyle = {
    fontFamily: tokens.font["font-family-mono"],
    fontWeight: tokens.font["font-weight-medium"],
    fontSize: "12px",
    letterSpacing: "0px",
    textAlign: "left",
    whiteSpace: "pre-wrap",
    wordBreak: "break-word",
    overflowX: "auto",
    maxWidth: "100%",
}

const LogsContainer = ({ logs, displayedLogData }) => {
    const d1 = func.epochToDateTime(Math.floor(logs.startTime / 1000))
    const d2 = func.epochToDateTime(Math.floor(logs.endTime / 1000))
    const totalLoaded = logs.logData.length
    const shown = displayedLogData.length
    const isFiltered = shown !== totalLoaded

    return (
        <Box width="100%" minWidth="0">
            <Text as="p" variant="bodyMd">
                <span>Fetched logs from </span>
                <span style={{ color: tokens.color["color-bg-success-strong"] }}>{d1}</span>
                <span> to </span>
                <span style={{ color: tokens.color["color-bg-success-strong"] }}>{d2}</span>
            </Text>
            {isFiltered ? (
                <Box paddingBlockStart="2">
                    <Text as="p" variant="bodySm" color="subdued">
                        Showing {shown} of {totalLoaded} loaded lines
                    </Text>
                </Box>
            ) : null}
            <Box paddingBlockStart="2">
                <Scrollable style={{ maxHeight: "min(70vh, 720px)", width: "100%" }} focusable>
                    <Box width="100%" padding="3" background="bg-surface-secondary" borderRadius="2">
                        <VerticalStack gap="1">
                            {displayedLogData.map((entry, idx) => (
                                <Box key={idx} width="100%" minWidth="0" style={logLineStyle}>
                                    [{func.epochToDateTime(entry.timestamp)}] {entry.log}
                                </Box>
                            ))}
                        </VerticalStack>
                    </Box>
                </Scrollable>
            </Box>
        </Box>
    )
}

export default LogsContainer
