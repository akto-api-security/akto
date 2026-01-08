import { useState, useEffect, useRef } from "react";
import { Spinner, Text, Box, Badge, Link } from "@shopify/polaris";
import TooltipWithLink from "../../../components/shared/TooltipWithLink";
import api from "../api";
import func from "../../../../../util/func";

// Cache to store fetched reputation scores
const reputationScoreCache = new Map();

// Get Badge tone based on score
// The colors in our dashboard are based on the risk level, 
// so we invert the tones here based on reputation score.
const getBadgeTone = (score) => {
  if (score == "HIGH") return "LOW"; // High reputation
  if (score == "MEDIUM") return "MEDIUM"; // Medium reputation
  if (score == "LOW") return "HIGH"; // Low reputation
  if (score == "N/A") return "LOW"; // Not available
};

// Check if IP address is valid
const isValidIpAddress = (ip) => ip && ip !== "-";

const getLinkForIp = (ip, source) => {
  switch (source) {
    case "ABUSEIPDB":
      return `https://www.abuseipdb.com/check/${ip}`;
    default:
      return null;
  }
}

const errorData = {
  score: "N/A",
  metadata: {},
  source: "N/A"
};

const IpReputationScore = ({ ipAddress }) => {
  const [reputationData, setReputationData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [isVisible, setIsVisible] = useState(false);
  const elementRef = useRef(null);

  // Format metadata for tooltip
  const formatTooltipContent = (data) => {
    const metadata = data.metadata || {};
    return (
      <Box padding="200" as="span">
        {metadata.countryCode && (
          <Text as="p">Country: {metadata.countryCode}</Text>
        )}
        {metadata.isp && (
          <Text as="p">ISP: {metadata.isp}</Text>
        )}
        {metadata.usageType && (
          <Text as="p">Usage Type: {metadata.usageType}</Text>
        )}
        {metadata.totalReports !== undefined && (
          <Text as="p">Total Reports: {metadata.totalReports}</Text>
        )}
        {metadata.isWhitelisted !== undefined && (
          <Text as="p">Whitelisted: {metadata.isWhitelisted ? "Yes" : "No"}</Text>
        )}
        {metadata.lastReportedAt && (
          <Text as="p">Last Reported: {new Date(metadata.lastReportedAt).toLocaleDateString()}</Text>
        )}
          <Text as="p" variant="bodySm" tone="subdued">
            Source: <Link url={getLinkForIp(ipAddress, data.source)} target="_blank" removeUnderline>{data.source}</Link>
          </Text>
      </Box>
    );
  };

  // Fetch reputation score
  const fetchReputationScore = async () => {
    if (!isValidIpAddress(ipAddress)) return;

    // Check cache first
    if (reputationScoreCache.has(ipAddress)) {
      setReputationData(reputationScoreCache.get(ipAddress));
      return;
    }

    setLoading(true);
    try {
      const response = await api.getIpReputationScore(ipAddress);
      if (response && response.reputationData) {
        const data = response.reputationData;
        reputationScoreCache.set(ipAddress, data);
        setReputationData(data);
      } else {
        setReputationData(errorData);
      }
    } catch (error) {
      setReputationData(errorData);
      // Silently handle error - no data will be displayed
    } finally {
      setLoading(false);
    }
  };

  // Intersection Observer for lazy loading
  useEffect(() => {
    const currentElement = elementRef.current;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && !isVisible) {
          setIsVisible(true);
        }
      },
      {
        rootMargin: "50px", // Start loading 50px before element is visible
        threshold: 0.1,
      }
    );

    if (currentElement) {
      observer.observe(currentElement);
    }

    return () => {
      if (currentElement) {
        observer.unobserve(currentElement);
      }
    };
  }, [isVisible]);

  // Fetch data when component becomes visible
  useEffect(() => {
    if (isVisible && !reputationData && !loading) {
      fetchReputationScore();
    }
  }, [isVisible, reputationData, loading]);

  if (!isValidIpAddress(ipAddress)) {
    return <Text as="span">-</Text>;
  }

  return (
    <Box ref={elementRef}>
      {loading && <Spinner size="small" />}
      {!loading && reputationData && (
        <TooltipWithLink content={formatTooltipContent(reputationData)} preferredPosition="above">
          <div key={reputationData.score} className={`badge-wrapper-${getBadgeTone(reputationData.score)}`}>
              <Badge size="small">
                {func.toSentenceCase(reputationData.score)}
              </Badge>
          </div>
        </TooltipWithLink>
      )}
      {!loading && !reputationData && (
        <Text as="span" tone="subdued" variant="bodySm">-</Text>
      )}
    </Box>
  );
};

export default IpReputationScore;
