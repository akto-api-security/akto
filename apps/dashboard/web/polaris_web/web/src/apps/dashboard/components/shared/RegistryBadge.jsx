import { Icon, Tooltip } from "@shopify/polaris"
import { CircleTickMajor } from "@shopify/polaris-icons"

const RegistryBadge = () => {
    const tooltipContent = (
        <div style={{ maxWidth: '280px' }}>
            <div style={{ fontWeight: 'bold', marginBottom: '8px' }}>Listed on the MCP Registry</div>
            <div style={{ marginBottom: '4px', fontSize: '13px', color: '#6d7175' }}>
                This MCP server appears in the official Model Context Protocol Registry.
            </div>
            <div style={{ marginBottom: '8px', fontSize: '13px', color: '#6d7175' }}>
                The registry is a community-maintained directory for MCP-compatible servers.
            </div>
            <a 
                href="https://registry.modelcontextprotocol.io/docs#/operations/list-servers-v0.1" 
                target="_blank" 
                rel="noopener noreferrer"
                style={{ color: '#005bd3', textDecoration: 'none', fontSize: '13px' }}
                onMouseOver={(e) => e.target.style.textDecoration = 'underline'}
                onMouseOut={(e) => e.target.style.textDecoration = 'none'}
            >
                View on MCP Registry →
            </a>
        </div>
    );
    
    return (
        <Tooltip content={tooltipContent} preferredPosition="above" width="wide">
            <span style={{ display: 'inline-flex' }}>
                <Icon source={CircleTickMajor} tone="success" />
            </span>
        </Tooltip>
    );
};

export default RegistryBadge;

