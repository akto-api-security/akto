import { Badge, Box, HorizontalStack, Text, Tooltip, Icon } from '@shopify/polaris'
import { useRef, useState } from 'react'
import func from '@/util/func'
import transform from '../onboarding/transform'
import observeFunc from "./transform"
import { isAgenticSecurityCategory, isMCPSecurityCategory, isEndpointSecurityCategory } from '../../../main/labelHelper'
import ShowListInBadge from '../../components/shared/ShowListInBadge'

const MAX_VISIBLE_SKILL_TAGS = 2

export const getMethod = (url, method, apiType) => {
    if (func.shouldHideHttpMethodForEndpoint({ apiType, url })) {
        return func.WEBSOCKET_METHOD_LABEL
    }
    if(isMCPSecurityCategory() || isAgenticSecurityCategory() || isEndpointSecurityCategory()){
        if(url.includes("tool")){
            return "TOOL";
        }else if(url.includes("/config/")){
            return "CONFIG";
        }else if(url.includes("skill")){
            return "SKILL";
        }else if(url.includes("resource")){
            return "RESOURCE";
        }else if(url.includes("prompt")){
            return "PROMPT";
        }else if(url.includes("server")){
            return "SERVER";
        }
        } else if (url.includes("v1/hooks")){
          return "HOOK"
        }
    return method;
}

export function MethodBox({method, methodBoxWidth, url, apiType}){
    const finalMethod = getMethod(url, method, apiType);
    const label = (
      <span
        style={{
          color: transform.getTextColor(finalMethod),
          fontSize: "14px",
          fontWeight: 500,
          lineHeight: "20px",
        }}
      >
        {finalMethod}
      </span>
    );
    return (
      <Box width={methodBoxWidth || "64px"}>
        <HorizontalStack align="end">
          {finalMethod === func.WEBSOCKET_METHOD_LABEL ? (
            <Tooltip content="WebSocket" dismissOnMouseOut>
              {label}
            </Tooltip>
          ) : label}
        </HorizontalStack>
      </Box>
    )
}

function GetPrettifyEndpoint({method, url, isNew, maxWidth, methodBoxWidth, guardrailEnabled, isMalicious, isMisconfigured, skillTags, apiType}){
    const ref = useRef(null)
    const localUrl = url || "/"
    const [copyActive, setCopyActive] = useState(false)
    const copyText = func.shouldHideHttpMethodForEndpoint({ apiType, url: localUrl })
      ? localUrl
      : getMethod(localUrl, method, apiType) + " " + localUrl
    return (
      <div
        style={{ display: "flex", gap: "4px" }}
        ref={ref}
        onMouseEnter={() => setCopyActive(true)}
        onMouseLeave={() => setCopyActive(false)}
      >
        <MethodBox method={method} methodBoxWidth={methodBoxWidth} url={url} apiType={apiType} />
        <Box width={maxWidth ? maxWidth : "30vw"}>
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              gap: "24px",
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <Box>
                <Text variant="bodyMd" fontWeight="medium" breakWord>
                  {observeFunc.getTruncatedUrl(localUrl)}
                </Text>
              </Box>
              {guardrailEnabled ? (
                <Tooltip content="Guardrails enabled for this endpoint" dismissOnMouseOut>
                  <div style={{ display: "inline-flex", alignItems: "center", cursor: "help" }}>
                    <img src="/public/mcp.svg" alt="Guardrails enabled" style={{ width: "24px", height: "24px" }} />
                  </div>
                </Tooltip>
              ) : null}
              {isMalicious ? (
                <Tooltip content="Malicious activity detected on this skill" dismissOnMouseOut>
                  <Badge status="critical" size="small">Malicious</Badge>
                </Tooltip>
              ) : null}
              {isMisconfigured ? (
                <Tooltip content="Misconfigured Claude settings detected on this skill" dismissOnMouseOut>
                  <Badge status="attention" size="small">Misconfigured</Badge>
                </Tooltip>
              ) : null}
              {skillTags && skillTags.length > 0 ? (
                <ShowListInBadge
                  itemsArr={skillTags}
                  maxItems={MAX_VISIBLE_SKILL_TAGS}
                  status="warning"
                  useTooltip={true}
                />
              ) : null}
              {copyActive ? (
                <div
                  onClick={(e) => {
                    e.stopPropagation();
                    func.copyToClipboard(
                      copyText,
                      ref,
                      "URL copied"
                    );
                  }}
                >
                  <Tooltip content="Copy endpoint" dismissOnMouseOut>
                    <svg
                      width="20"
                      height="20"
                      viewBox="0 0 20 20"
                      fill="none"
                      xmlns="http://www.w3.org/2000/svg"
                    >
                      <path
                        d="M12.8 12.8V14.76C12.8 15.5441 12.8 15.9361 12.6474 16.2356C12.5132 16.499 12.299 16.7132 12.0356 16.8474C11.7361 17 11.3441 17 10.56 17H5.24C4.45593 17 4.06389 17 3.76441 16.8474C3.50099 16.7132 3.28681 16.499 3.15259 16.2356C3 15.9361 3 15.5441 3 14.76V9.44C3 8.65593 3 8.26389 3.15259 7.96441C3.28681 7.70099 3.50099 7.48681 3.76441 7.35259C4.06389 7.2 4.45593 7.2 5.24 7.2H7.2M9.44 12.8H14.76C15.5441 12.8 15.9361 12.8 16.2356 12.6474C16.499 12.5132 16.7132 12.299 16.8474 12.0356C17 11.7361 17 11.3441 17 10.56V5.24C17 4.45593 17 4.06389 16.8474 3.76441C16.7132 3.50099 16.499 3.28681 16.2356 3.15259C15.9361 3 15.5441 3 14.76 3H9.44C8.65593 3 8.26389 3 7.96441 3.15259C7.70099 3.28681 7.48681 3.50099 7.35259 3.76441C7.2 4.06389 7.2 4.45593 7.2 5.24V10.56C7.2 11.3441 7.2 11.7361 7.35259 12.0356C7.48681 12.299 7.70099 12.5132 7.96441 12.6474C8.26389 12.8 8.65593 12.8 9.44 12.8Z"
                        stroke="#8C9196"
                        strokeWidth="1.68"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                    </svg>
                  </Tooltip>
                  <Box ref={ref} />
                </div>
              ) : null}
            </div>
            <Box>{isNew ? <Badge size="small">New</Badge> : null}</Box>
          </div>
        </Box>
      </div>
    );
}


export default GetPrettifyEndpoint