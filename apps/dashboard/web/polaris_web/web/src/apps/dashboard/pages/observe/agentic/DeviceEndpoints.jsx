import React, { useState, useMemo, useCallback, useRef } from "react";
import { createPortal } from "react-dom";
import Highcharts from "highcharts";
import { HighchartsReact } from "highcharts-react-official";
import {
    Card, HorizontalStack, Text,
} from "@shopify/polaris";
import { AgGridReact } from "ag-grid-react";
import { ModuleRegistry, AllCommunityModule } from "ag-grid-community";
import { LicenseManager, themeQuartz, AllEnterpriseModule } from "ag-grid-enterprise";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import PageWithMultipleCards from "@/apps/dashboard/components/layouts/PageWithMultipleCards";

ModuleRegistry.registerModules([AllCommunityModule, AllEnterpriseModule]);

LicenseManager.setLicenseKey(
    "[TRIAL]_this_{AG_Charts_and_AG_Grid}_Enterprise_key_{AG-129492}_is_granted_for_evaluation_only___Use_in_production_is_not_permitted___Please_report_misuse_to_legal@ag-grid.com___For_help_with_purchasing_a_production_key_please_contact_info@ag-grid.com___You_are_granted_a_{Single_Application}_Developer_License_for_one_application_only___All_Front-End_JavaScript_developers_working_on_the_application_would_need_to_be_licensed___This_key_will_deactivate_on_{18 June 2026}____[v3]_[0102]_MTc4MTczNzIwMDAwMA==d27c8a4487e577f42d9980e95824f43c"
);

const myTheme = themeQuartz.withParams({
    accentColor: "#9642FC",
    borderColor: "#E1E3E5",
    borderRadius: 4,
    browserColorScheme: "light",
    cellTextColor: "#202223",
    columnBorder: false,
    fontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
    fontSize: 12,
    foregroundColor: "#202223",
    headerFontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
    headerRowBorder: true,
    headerTextColor: "#6D7175",
    iconSize: 16,
    rowBorder: true,
    spacing: 8,
    wrapperBorder: true,
    wrapperBorderRadius: 8,
    headerFontSize: 12,
    headerFontWeight: 500,
    checkboxBorderRadius: 4,
});

// Theme variant used when a search bar sits above the grid inside a shared container
const myThemeInner = myTheme.withParams({
    wrapperBorder: false,
    wrapperBorderRadius: 0,
});

// ─── Chart data ─────────────────────────────────────────────────────────────

const MONTHS = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];

const STAT_SPARKLINES = {
    endpoints: [4200,4500,4800,5100,5300,5500,5700,5900,6000,6100,6350,6403],
    users:     [2800,3000,3200,3400,3500,3700,3800,3900,4000,4050,4150,4203],
    violations:[800, 850, 900, 950,1000,1050,1100,1150,1200,1280,1370,1400],
};

const OS_TREND = {
    mac:     [2500,2700,2900,3100,3200,3300,3500,3600,3700,3800,3900,4000],
    windows: [1200,1250,1300,1350,1400,1450,1500,1550,1600,1650,1700,1800],
    linux:   [500, 550, 600, 650, 700, 750, 700, 750, 700, 650, 750, 603],
};

const VIOLATIONS_BY_SEVERITY = [
    { name: "Critical", y: 450, color: "#DC2626" },
    { name: "High",     y: 380, color: "#F97316" },
    { name: "Medium",   y: 320, color: "#EAB308" },
    { name: "Low",      y: 250, color: "#D1D5DB" },
];

// ─── Dummy data ──────────────────────────────────────────────────────────────

const DEVICE_FLAT_DATA = [
    { path: ["NYC-JDOE-MAC01"], endpoint: "NYC-JDOE-MAC01", os: "mac", userCount: 5, riskScore: 4.8, username: "John Doe", group: "Engineering", role: "Software Engineer", violations: { critical:2, high:0, medium:0, low:0 }, lastTraffic: "2h ago", hasPersonalAccount: true },
    { path: ["NYC-JDOE-WIN11"], endpoint: "NYC-JDOE-WIN11", os: "windows", userCount: 2, riskScore: 3.2, username: "John Doe", group: "Engineering", role: "Software Engineer", violations: { critical:0, high:0, medium:1, low:0 }, lastTraffic: "1d ago" },
    { path: ["NYC-JDOE-WIN11","copilot365-jdoe"], endpoint: "Microsoft Copilot 365", type: "AI Agent" },
    { path: ["NYC-JDOE-WIN11","mcp-github-jdoe"], endpoint: "github-mcp",            type: "MCP Server" },
    { path: ["NYC-JDOE-WIN11","gpt4-jdoe"],       endpoint: "GPT-4o",                type: "LLM" },
    { path: ["NYC-JDOE-MAC01","cursor-cli"],     endpoint: "Cursor CLI",             type: "AI Agent",   skillCount: 1   },
    { path: ["NYC-JDOE-MAC01","cursor-code"],    endpoint: "Cursor code",            type: "AI Agent",   skillCount: 407 },
    { path: ["NYC-JDOE-MAC01","mcp-akto"],       endpoint: "mcp.akto.io",            type: "MCP Server"                  },
    { path: ["NYC-JDOE-MAC01","chatgpt"],        endpoint: "ChatGPT",                type: "AI Agent"                    },
    { path: ["NYC-JDOE-MAC01","razorpay-stdio"], endpoint: "razorpay-stdio",         type: "MCP Server"                  },
    { path: ["NYC-JDOE-MAC01","gemini"],         endpoint: "gemini",                 type: "LLM"                         },

    { path: ["BER-TSMITH-MAC02"], endpoint: "BER-TSMITH-MAC02", os: "mac", userCount: 23, riskScore: 4.7, username: "Traun Smith", group: "Engineering", role: "Frontend Developer", violations: { critical:1, high:1, medium:3, low:2 }, lastTraffic: "45m ago", hasPersonalAccount: true },
    { path: ["BER-TSMITH-MAC02","vscode"],         endpoint: "VS Code",        type: "AI Agent",   skillCount: 12 },
    { path: ["BER-TSMITH-MAC02","github-copilot"], endpoint: "GitHub Copilot", type: "AI Agent"                   },
    { path: ["BER-TSMITH-MAC02","mcp-github"],     endpoint: "github-mcp",     type: "MCP Server"                 },

    { path: ["SF-MWILSON-WIN10"], endpoint: "SF-MWILSON-WIN10", os: "windows", userCount: 23, riskScore: 4.5, username: "Mark Wilson", group: "Human Resources", role: "Lead HR", violations: { critical:2, high:4, medium:0, low:1 }, lastTraffic: "1d ago" },
    { path: ["SF-MWILSON-MAC01"], endpoint: "SF-MWILSON-MAC01", os: "mac", userCount: 1, riskScore: 2.8, username: "Mark Wilson", group: "Human Resources", role: "Lead HR", violations: { critical:0, high:0, medium:0, low:1 }, lastTraffic: "3d ago" },
    { path: ["SF-MWILSON-MAC01","claude-mwilson"], endpoint: "Claude Desktop", type: "AI Agent" },
    { path: ["SF-MWILSON-MAC01","mcp-notion-mw"],  endpoint: "notion-mcp",     type: "MCP Server" },
    { path: ["SF-MWILSON-WIN10","copilot365"],     endpoint: "Microsoft Copilot 365", type: "AI Agent"   },
    { path: ["SF-MWILSON-WIN10","teams-bot"],      endpoint: "Teams AI Bot",          type: "AI Agent"   },
    { path: ["SF-MWILSON-WIN10","mcp-sharepoint"], endpoint: "sharepoint-mcp",        type: "MCP Server" },
    { path: ["SF-MWILSON-WIN10","gpt4"],           endpoint: "GPT-4o",                type: "LLM"        },

    { path: ["BER-DWILSON-MAC02"], endpoint: "BER-DWILSON-MAC02", os: "mac", userCount: 10, riskScore: 4.5, username: "David Wilson", group: "Engineering", role: "Full Stack Developer", violations: { critical:0, high:1, medium:0, low:0 }, lastTraffic: "3h ago" },
    { path: ["BER-DWILSON-MAC02","claude-desktop"], endpoint: "Claude Desktop",    type: "AI Agent",   skillCount: 23 },
    { path: ["BER-DWILSON-MAC02","mcp-slack"],      endpoint: "slack-mcp",         type: "MCP Server"               },
    { path: ["BER-DWILSON-MAC02","mcp-jira"],       endpoint: "jira-mcp",          type: "MCP Server"               },
    { path: ["BER-DWILSON-MAC02","claude-3"],       endpoint: "claude-3.7-sonnet", type: "LLM"                      },

    { path: ["SF-STAYLOR-MAC01"], endpoint: "SF-STAYLOR-MAC01", os: "mac", userCount: 30, riskScore: 4.5, username: "Sarah Taylor", group: "Engineering", role: "DevOps Engineer", violations: { critical:0, high:0, medium:0, low:5 }, lastTraffic: "6h ago" },
    { path: ["SF-STAYLOR-MAC01","cursor-ai"], endpoint: "Cursor AI",      type: "AI Agent",   skillCount: 88 },
    { path: ["SF-STAYLOR-MAC01","mcp-k8s"],   endpoint: "kubernetes-mcp", type: "MCP Server"               },
    { path: ["SF-STAYLOR-MAC01","mcp-aws"],   endpoint: "aws-mcp",        type: "MCP Server"               },
    { path: ["SF-STAYLOR-MAC01","gpt4-mini"], endpoint: "GPT-4o-mini",    type: "LLM"                      },

    { path: ["SF-LTHOMAS-MAC02"], endpoint: "SF-LTHOMAS-MAC02", os: "mac", userCount: 20, riskScore: 4.3, username: "Linda Thomas", group: "Engineering", role: "QA Engineer", violations: { critical:1, high:2, medium:1, low:0 }, lastTraffic: "6h ago" },
    { path: ["SF-LTHOMAS-MAC02","playwright-ai"], endpoint: "Playwright AI", type: "AI Agent", skillCount: 5 },
    { path: ["SF-LTHOMAS-MAC02","gemini-pro"],    endpoint: "gemini-pro",    type: "LLM"                   },

    { path: ["SF-RCLARK-MAC01"], endpoint: "SF-RCLARK-MAC01", os: "mac", userCount: 19, riskScore: 4.3, username: "Robert Clark", group: "Engineering", role: "Mobile App Developer", violations: { critical:0, high:1, medium:0, low:0 }, lastTraffic: "6h ago" },
    { path: ["SF-RCLARK-MAC01","cursor-ai2"],   endpoint: "Cursor AI",   type: "AI Agent",   skillCount: 14 },
    { path: ["SF-RCLARK-MAC01","mcp-xcode"],    endpoint: "xcode-mcp",   type: "MCP Server"               },
    { path: ["SF-RCLARK-MAC01","claude-haiku"], endpoint: "claude-haiku",type: "LLM"                      },

    { path: ["SF-JLEWIS-WIN10"], endpoint: "SF-JLEWIS-WIN10", os: "windows", userCount: 34, riskScore: 4.3, username: "Jennifer Lewis", group: "Engineering", role: "Data Engineer", violations: { critical:4, high:1, medium:0, low:0 }, lastTraffic: "6h ago", hasPersonalAccount: true },
    { path: ["SF-JLEWIS-LIN01"], endpoint: "SF-JLEWIS-LIN01", os: "linux", userCount: 5, riskScore: 3.1, username: "Jennifer Lewis", group: "Engineering", role: "Data Engineer", violations: { critical:0, high:1, medium:0, low:0 }, lastTraffic: "2d ago" },
    { path: ["SF-JLEWIS-LIN01","jupyter-jlewis"],  endpoint: "Jupyter AI",    type: "AI Agent",   skillCount: 4 },
    { path: ["SF-JLEWIS-LIN01","ollama-jlewis"],   endpoint: "Ollama",        type: "LLM" },
    { path: ["SF-JLEWIS-LIN01","mcp-pg-jlewis"],   endpoint: "postgres-mcp",  type: "MCP Server" },
    { path: ["SF-JLEWIS-MAC01"], endpoint: "SF-JLEWIS-MAC01", os: "mac", userCount: 3, riskScore: 2.4, username: "Jennifer Lewis", group: "Engineering", role: "Data Engineer", violations: { critical:0, high:0, medium:1, low:0 }, lastTraffic: "5d ago" },
    { path: ["SF-JLEWIS-MAC01","cursor-jlewis"],   endpoint: "Cursor AI",     type: "AI Agent",   skillCount: 6 },
    { path: ["SF-JLEWIS-MAC01","claude-jlewis"],   endpoint: "claude-3.7-sonnet", type: "LLM" },
    { path: ["SF-JLEWIS-WIN10","copilot-data"],   endpoint: "GitHub Copilot", type: "AI Agent"   },
    { path: ["SF-JLEWIS-WIN10","mcp-databricks"], endpoint: "databricks-mcp", type: "MCP Server" },
    { path: ["SF-JLEWIS-WIN10","gpt4-data"],      endpoint: "GPT-4o",         type: "LLM"        },

    { path: ["SF-WHALL-WIN10"], endpoint: "SF-WHALL-WIN10", os: "windows", userCount: 19, riskScore: 4.1, username: "William Hall", group: "Engineering", role: "Product Manager", violations: { critical:0, high:0, medium:1, low:0 }, lastTraffic: "6h ago" },
    { path: ["SF-WHALL-WIN10","copilot365-pm"], endpoint: "Microsoft Copilot 365", type: "AI Agent"   },
    { path: ["SF-WHALL-WIN10","mcp-notion"],    endpoint: "notion-mcp",            type: "MCP Server" },

    { path: ["SF-PYOUNG-WIN10"], endpoint: "SF-PYOUNG-WIN10", os: "windows", userCount: 3, riskScore: 4.1, username: "Patricia Young", group: "Finance", role: "Finance Manager", violations: { critical:0, high:0, medium:1, low:1 }, lastTraffic: "6h ago" },
    { path: ["SF-PYOUNG-WIN10","copilot365-fin"], endpoint: "Microsoft Copilot 365", type: "AI Agent"   },
    { path: ["SF-PYOUNG-WIN10","mcp-sap"],        endpoint: "sap-mcp",               type: "MCP Server" },

    { path: ["SF-CKING-MAC01"], endpoint: "SF-CKING-MAC01", os: "mac", userCount: 1, riskScore: 4.1, username: "Charles King", group: "Finance", role: "Finance Manager", violations: { critical:0, high:0, medium:1, low:1 }, lastTraffic: "6h ago" },
    { path: ["SF-CKING-MAC01","claude-fin"],      endpoint: "Claude Desktop", type: "AI Agent"   },
    { path: ["SF-CKING-MAC01","mcp-quickbooks"],  endpoint: "quickbooks-mcp", type: "MCP Server" },

    { path: ["NYC-JANDERSON-MAC01"], endpoint: "NYC-JANDERSON-MAC01", os: "mac", userCount: 4, riskScore: 3.8, username: "James Anderson", group: "Engineering", role: "VP of Engineering", violations: { critical:0, high:0, medium:2, low:0 }, lastTraffic: "5h ago" },
    { path: ["NYC-JANDERSON-MAC01","cursor-vp"],  endpoint: "Cursor AI",  type: "AI Agent",   skillCount: 9 },
    { path: ["NYC-JANDERSON-MAC01","mcp-linear"], endpoint: "linear-mcp", type: "MCP Server"              },
    { path: ["NYC-JANDERSON-MAC01","gpt4-vp"],    endpoint: "GPT-4o",     type: "LLM"                     },

    { path: ["LON-RJOHNSON-WIN11"], endpoint: "LON-RJOHNSON-WIN11", os: "windows", userCount: 15, riskScore: 3.8, username: "Robert Johnson", group: "Sales", role: "Sales Engineer", violations: { critical:0, high:1, medium:2, low:4 }, lastTraffic: "12h ago" },
    { path: ["LON-RJOHNSON-WIN11","copilot-win"], endpoint: "Microsoft Copilot", type: "AI Agent"   },
    { path: ["LON-RJOHNSON-WIN11","mcp-crm"],     endpoint: "salesforce-mcp",   type: "MCP Server" },

    { path: ["TKY-AMATSUDA-LIN01"], endpoint: "TKY-AMATSUDA-LIN01", os: "linux", userCount: 8, riskScore: 2.1, username: "Akira Matsuda", group: "Data Science", role: "ML Engineer", violations: { critical:0, high:0, medium:1, low:1 }, lastTraffic: "2d ago" },
    { path: ["TKY-AMATSUDA-LIN01","jupyter-ai"],  endpoint: "Jupyter AI",   type: "AI Agent",   skillCount: 3 },
    { path: ["TKY-AMATSUDA-LIN01","ollama"],       endpoint: "Ollama",       type: "LLM"                     },
    { path: ["TKY-AMATSUDA-LIN01","mcp-postgres"], endpoint: "postgres-mcp", type: "MCP Server"              },
];

// Build lookups: username → all device rows (array), device path → child count
const devicesByUsername = {};
const deviceChildCount = {};

DEVICE_FLAT_DATA.forEach(row => {
    if (row.path.length === 1 && row.username) {
        if (!devicesByUsername[row.username]) devicesByUsername[row.username] = [];
        devicesByUsername[row.username].push(row);
    }
    if (row.path.length === 2) {
        const id = row.path[0];
        deviceChildCount[id] = (deviceChildCount[id] || 0) + 1;
    }
});

const USER_FLAT_DATA = [
    { path: ["john-doe"],       username: "John Doe",       riskScore: 4.8, group: "Engineering",    role: "Software Engineer",   violations: { critical:2, high:0, medium:0, low:0 }, lastTraffic: "2h ago",  hasPersonalAccount: true },
    { path: ["traun-smith"],    username: "Traun Smith",    riskScore: 4.7, group: "Engineering",    role: "Frontend Developer",  violations: { critical:1, high:1, medium:3, low:2 }, lastTraffic: "45m ago", hasPersonalAccount: true },
    { path: ["mark-wilson"],    username: "Mark Wilson",    riskScore: 4.5, group: "Human Resources",role: "Lead HR",             violations: { critical:2, high:4, medium:0, low:1 }, lastTraffic: "1d ago"  },
    { path: ["david-wilson"],   username: "David Wilson",   riskScore: 4.5, group: "Engineering",    role: "Full Stack Developer",violations: { critical:0, high:1, medium:0, low:0 }, lastTraffic: "3h ago"  },
    { path: ["sarah-taylor"],   username: "Sarah Taylor",   riskScore: 4.5, group: "Engineering",    role: "DevOps Engineer",     violations: { critical:0, high:0, medium:0, low:5 }, lastTraffic: "6h ago"  },
    { path: ["linda-thomas"],   username: "Linda Thomas",   riskScore: 4.3, group: "Engineering",    role: "QA Engineer",         violations: { critical:1, high:2, medium:1, low:0 }, lastTraffic: "6h ago"  },
    { path: ["robert-clark"],   username: "Robert Clark",   riskScore: 4.3, group: "Engineering",    role: "Mobile App Developer",violations: { critical:0, high:1, medium:0, low:0 }, lastTraffic: "6h ago"  },
    { path: ["jennifer-lewis"], username: "Jennifer Lewis", riskScore: 4.3, group: "Engineering",    role: "Data Engineer",       violations: { critical:4, high:1, medium:0, low:0 }, lastTraffic: "6h ago",  hasPersonalAccount: true },
    { path: ["william-hall"],   username: "William Hall",   riskScore: 4.1, group: "Engineering",    role: "Product Manager",     violations: { critical:0, high:0, medium:1, low:0 }, lastTraffic: "6h ago"  },
    { path: ["patricia-young"], username: "Patricia Young", riskScore: 4.1, group: "Finance",        role: "Finance Manager",     violations: { critical:0, high:0, medium:1, low:1 }, lastTraffic: "6h ago"  },
    { path: ["charles-king"],   username: "Charles King",   riskScore: 4.1, group: "Finance",        role: "Finance Manager",     violations: { critical:0, high:0, medium:1, low:1 }, lastTraffic: "6h ago"  },
    { path: ["james-anderson"], username: "James Anderson", riskScore: 3.8, group: "Engineering",    role: "VP of Engineering",   violations: { critical:0, high:0, medium:2, low:0 }, lastTraffic: "5h ago"  },
    { path: ["robert-johnson"], username: "Robert Johnson", riskScore: 3.8, group: "Sales",          role: "Sales Engineer",      violations: { critical:0, high:1, medium:2, low:4 }, lastTraffic: "12h ago" },
    { path: ["akira-matsuda"],  username: "Akira Matsuda",  riskScore: 2.1, group: "Data Science",   role: "ML Engineer",         violations: { critical:0, high:0, medium:1, low:1 }, lastTraffic: "2d ago"  },
];

// ─── Chart configs ───────────────────────────────────────────────────────────

function makeSparklineConfig(data, color) {
    const min = Math.min(...data), max = Math.max(...data);
    const pad = (max - min) * 0.2;
    return {
        chart: { type:"area", height:50, width:140, backgroundColor:"transparent", margin:[2,0,2,0], spacing:[0,0,0,0], animation:false },
        title:null, subtitle:{text:null}, credits:{enabled:false}, exporting:{enabled:false},
        xAxis:{visible:false}, yAxis:{visible:false, min:min-pad, max:max+pad},
        legend:{enabled:false}, tooltip:{enabled:false},
        plotOptions:{ area:{ fillColor:{ linearGradient:{x1:0,y1:0,x2:0,y2:1}, stops:[[0,Highcharts.color(color).setOpacity(0.25).get("rgba")],[1,Highcharts.color(color).setOpacity(0).get("rgba")]] }, lineWidth:2, marker:{enabled:false}, states:{hover:{enabled:false}}, enableMouseTracking:false } },
        series:[{data,color}],
    };
}

function makeOsTrendConfig() {
    return {
        chart:{type:"line",height:260,backgroundColor:"transparent",style:{fontFamily:"Inter, -apple-system, sans-serif"},margin:[24,20,64,52]},
        title:null, credits:{enabled:false}, exporting:{enabled:false},
        xAxis:{categories:MONTHS,labels:{style:{fontSize:"11px",color:"#8C9196"}},lineColor:"#DFE3E8",tickColor:"transparent"},
        yAxis:{title:null,labels:{style:{fontSize:"11px",color:"#8C9196"}},gridLineColor:"#F1F2F3"},
        legend:{align:"left",verticalAlign:"bottom",itemStyle:{fontSize:"12px",fontWeight:"400",color:"#6D7175"},symbolRadius:4,margin:12},
        tooltip:{shared:true,backgroundColor:"white",borderColor:"#DFE3E8",borderRadius:8,style:{fontSize:"12px"}},
        plotOptions:{line:{marker:{enabled:false},lineWidth:2}},
        series:[
            {name:"Mac",     data:OS_TREND.mac,     color:"#7C3AED"},
            {name:"Windows", data:OS_TREND.windows, color:"#10B981"},
            {name:"Linux",   data:OS_TREND.linux,   color:"#F59E0B"},
        ],
    };
}

function makeViolationsDonutConfig() {
    return {
        chart:{type:"pie",height:240,backgroundColor:"transparent",style:{fontFamily:"Inter, -apple-system, sans-serif"},margin:[8,0,48,0]},
        title:null, credits:{enabled:false}, exporting:{enabled:false},
        tooltip:{pointFormat:"<b>{point.y}</b> ({point.percentage:.0f}%)",backgroundColor:"white",borderColor:"#DFE3E8",borderRadius:8,style:{fontSize:"12px"}},
        plotOptions:{pie:{innerSize:"55%",size:"85%",center:["50%","45%"],borderWidth:2,borderColor:"white",dataLabels:{enabled:false},showInLegend:true}},
        legend:{align:"center",verticalAlign:"bottom",itemStyle:{fontSize:"12px",fontWeight:"400",color:"#6D7175"},symbolRadius:4,margin:10},
        series:[{name:"Violations",data:VIOLATIONS_BY_SEVERITY}],
    };
}

// ─── Stat + chart cards ──────────────────────────────────────────────────────

function StatRow({ label, value, delta, sparklineData, color, hasBorder }) {
    const opts = useMemo(() => makeSparklineConfig(sparklineData, color), [sparklineData, color]);
    return (
        <div style={{ padding:"16px 20px", borderBottom:hasBorder?"1px solid #F1F2F3":"none", display:"flex", alignItems:"center", justifyContent:"space-between", gap:12, flex:1 }}>
            <div>
                <Text variant="headingSm" fontWeight="semibold">{label}</Text>
                <div style={{ display:"flex", alignItems:"center", gap:6, marginTop:6 }}>
                    <Text variant="headingXl" fontWeight="bold">{value.toLocaleString()}</Text>
                    <span style={{ fontSize:13, fontWeight:600, color:"#16A34A" }}>+{delta}</span>
                </div>
            </div>
            <div style={{ flexShrink:0, lineHeight:0 }}>
                <HighchartsReact highcharts={Highcharts} options={opts} immutable />
            </div>
        </div>
    );
}


function ChartPanel({ title, children }) {
    return (
        <div style={{ padding:"16px 20px", display:"flex", flexDirection:"column" }}>
            <Text variant="headingMd" fontWeight="semibold">{title}</Text>
            <div style={{ flex:1 }}>{children}</div>
        </div>
    );
}

function TopSection() {
    return (
        <div style={{ display:"grid", gridTemplateColumns:"320px 1fr 298px", gap:16 }}>
            <Card padding="0">
                <div style={{ display:"flex", flexDirection:"column", justifyContent:"space-between", height:"100%" }}>
                    <StatRow label="Total Endpoints"  value={6403} delta={21} sparklineData={STAT_SPARKLINES.endpoints}  color="#7C3AED" hasBorder />
                    <StatRow label="Users"            value={4203} delta={20} sparklineData={STAT_SPARKLINES.users}      color="#2563EB" hasBorder />
                    <StatRow label="Total Violations" value={1400} delta={24} sparklineData={STAT_SPARKLINES.violations} color="#DC2626" hasBorder={false} />
                </div>
            </Card>
            <Card padding="0">
                <ChartPanel title="Endpoints Over Time by OS Type">
                    <HighchartsReact highcharts={Highcharts} options={makeOsTrendConfig()}/>
                </ChartPanel>
            </Card>
            <Card padding="0">
                <ChartPanel title="Violations by Severity">
                    <HighchartsReact highcharts={Highcharts} options={makeViolationsDonutConfig()}/>
                </ChartPanel>
            </Card>
        </div>
    );
}

// ─── OS icons ────────────────────────────────────────────────────────────────

const MacIcon = () => (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="#202223" style={{ flexShrink: 0 }}>
        <path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.8-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"/>
    </svg>
);

const WindowsIcon = () => (
    <svg width="15" height="15" viewBox="0 0 24 24" style={{ flexShrink: 0 }}>
        <path fill="#00A4EF" d="M0 3.449L9.75 2.1v9.451H0m10.949-9.602L24 0v11.4H10.949M0 12.6h9.75v9.451L0 20.699M10.949 12.6H24V24l-12.9-1.801"/>
    </svg>
);

const LinuxIcon = () => (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="#F59E0B" style={{ flexShrink: 0 }}>
        <path d="M12.504 0c-.155 0-.315.008-.48.021-4.226.333-3.105 4.807-3.17 6.298-.076 1.092-.3 1.953-1.05 3.02-.885 1.051-2.127 2.75-2.716 4.521-.278.832-.41 1.684-.287 2.489a.424.424 0 00-.11.135c-.26.268-.45.6-.663.839-.199.199-.485.267-.797.4-.313.136-.658.269-.864.68-.09.189-.136.394-.132.602 0 .199.027.4.055.536.058.399.116.728.04.97-.249.68-.28 1.145-.106 1.484.174.334.535.47.94.601.81.2 1.91.135 2.774.6.926.466 1.866.67 2.616.47.526-.116.97-.464 1.208-.946.587-.003 1.23-.269 2.31-.269 1.083 0 1.73.267 2.31.269.24.482.68.83 1.21.946.75.2 1.69-.004 2.616-.47.865-.465 1.963-.4 2.775-.6.406-.133.766-.268.94-.602.174-.339.142-.804-.106-1.483-.076-.242-.019-.572.04-.97.028-.136.055-.337.055-.538.003-.207-.043-.411-.131-.6-.207-.411-.553-.545-.865-.682-.312-.133-.598-.2-.797-.4-.213-.239-.404-.572-.664-.839a.424.424 0 00-.11-.135c.122-.805-.009-1.657-.287-2.489-.589-1.771-1.831-3.47-2.716-4.521-.75-1.067-.974-1.928-1.05-3.02-.066-1.491 1.056-5.965-3.17-6.298-.165-.013-.324-.021-.48-.021z"/>
    </svg>
);

const OsIcon = ({ os }) => {
    if (os === "mac") return <MacIcon />;
    if (os === "windows") return <WindowsIcon />;
    return <LinuxIcon />;
};

// ─── Shared cell renderers ───────────────────────────────────────────────────

const TYPE_STYLES = {
    "AI Agent":   { bg: "#EFF6FF", color: "#1D4ED8", border: "#BFDBFE" },
    "MCP Server": { bg: "#FFFBEB", color: "#92400E", border: "#FDE68A" },
    "LLM":        { bg: "#F0FDF4", color: "#166534", border: "#BBF7D0" },
};

function TypeBadge({ type }) {
    if (!type) return null;
    const s = TYPE_STYLES[type] || { bg: "#F3F4F6", color: "#374151", border: "#E5E7EB" };
    return (
        <span style={{
            display: "inline-flex", alignItems: "center",
            padding: "1px 7px", borderRadius: 12,
            fontSize: 11, fontWeight: 500, lineHeight: "18px",
            background: s.bg, color: s.color,
            border: `1px solid ${s.border}`,
            whiteSpace: "nowrap",
        }}>
            {type}
        </span>
    );
}

function SkillBadge({ count }) {
    if (!count) return null;
    return (
        <span style={{
            display: "inline-flex", alignItems: "center",
            padding: "1px 7px", borderRadius: 12,
            fontSize: 11, fontWeight: 500, lineHeight: "18px",
            background: "#F3F4F6", color: "#374151",
            border: "1px solid #E5E7EB",
            whiteSpace: "nowrap",
        }}>
            {count} skills
        </span>
    );
}

function CountBadge({ count, style }) {
    return (
        <span style={{
            display: "inline-flex", alignItems: "center", justifyContent: "center",
            padding: "1px 7px", borderRadius: 12,
            fontSize: 11, fontWeight: 600, lineHeight: "18px",
            background: "#F1F2F3", color: "#6D7175",
            border: "1px solid #E1E3E5",
            whiteSpace: "nowrap",
            ...style,
        }}>
            +{count}
        </span>
    );
}

function getRiskColor(score) {
    if (score >= 4.5) return { bg: "#FEE2E2", color: "#DC2626" };
    if (score >= 4.0) return { bg: "#FFEDD5", color: "#EA580C" };
    if (score >= 3.5) return { bg: "#FEF9C3", color: "#CA8A04" };
    return { bg: "#F0FDF4", color: "#16A34A" };
}

function RiskScoreCellRenderer({ value }) {
    if (value == null) return null;
    const { bg, color } = getRiskColor(value);
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%" }}>
            <span style={{
                display: "inline-flex", alignItems: "center", justifyContent: "center",
                width: 44, height: 24, borderRadius: 12,
                fontSize: 12, fontWeight: 600,
                background: bg, color,
            }}>
                {value.toFixed(1)}
            </span>
        </div>
    );
}

const SEVERITY_COLORS = {
    critical: { bg: "#DF2909", text: "#FFFBFB" },
    high:     { bg: "#FED3D1", text: "#202223" },
    medium:   { bg: "#FFD79D", text: "#202223" },
    low:      { bg: "#E4E5E7", text: "#202223" },
};

function ViolationsCellRenderer({ data }) {
    if (!data?.violations) return null;
    const { critical, high, medium, low } = data.violations;
    const parts = [
        { key: "critical", count: critical },
        { key: "high",     count: high     },
        { key: "medium",   count: medium   },
        { key: "low",      count: low      },
    ].filter(p => p.count > 0);

    if (!parts.length) return null;

    return (
        <div style={{ display: "flex", alignItems: "center", gap: 4, height: "100%" }}>
            {parts.map(p => (
                <span key={p.key} style={{
                    display: "inline-flex", alignItems: "center", justifyContent: "center",
                    minWidth: 22, height: 22, padding: "0 5px", borderRadius: 11,
                    fontSize: 11, fontWeight: 700,
                    background: SEVERITY_COLORS[p.key].bg,
                    color: SEVERITY_COLORS[p.key].text,
                }}>
                    {p.count}
                </span>
            ))}
        </div>
    );
}

// ─── Endpoint count badge with hover tooltip ─────────────────────────────────

function EndpointCountBadge({ count, children }) {
    const [show, setShow] = useState(false);
    const ref = useRef(null);
    const [pos, setPos] = useState({ top: 0, left: 0 });

    const handleEnter = () => {
        if (ref.current) {
            const r = ref.current.getBoundingClientRect();
            setPos({ top: r.bottom + 6, left: r.left });
        }
        setShow(true);
    };

    return (
        <>
            <span
                ref={ref}
                onMouseEnter={handleEnter}
                onMouseLeave={() => setShow(false)}
                style={{
                    display: "inline-flex", alignItems: "center", justifyContent: "center",
                    minWidth: 20, height: 20, padding: "0 6px", borderRadius: 10,
                    fontSize: 11, fontWeight: 600,
                    background: "#F1F2F3", color: "#6D7175",
                    cursor: "default",
                }}
            >
                +{count}
            </span>
            {show && createPortal(
                <div style={{
                    position: "fixed", top: pos.top, left: pos.left,
                    background: "white",
                    border: "1px solid #E1E3E5",
                    borderRadius: 8,
                    padding: "6px 0",
                    boxShadow: "0 4px 16px rgba(0,0,0,0.10)",
                    zIndex: 99999,
                    minWidth: 200,
                    fontFamily: "Inter, sans-serif",
                    pointerEvents: "none",
                }}>
                    {children}
                </div>,
                document.body
            )}
        </>
    );
}

// ─── Devices: endpoint cell (used as innerRenderer in autoGroupColumnDef) ────

const GroupUsersIcon = ({ color = "#8C9196" }) => (
    <svg width="14" height="14" viewBox="0 0 20 20" fill={color} style={{ flexShrink: 0 }}>
        <path d="M9 6a3 3 0 11-6 0 3 3 0 016 0zM17 6a3 3 0 11-6 0 3 3 0 016 0zM12.93 17c.046-.327.07-.66.07-1a6.97 6.97 0 00-1.5-4.33A5 5 0 0119 16v1h-6.07zM6 11a5 5 0 015 5v1H1v-1a5 5 0 015-5z"/>
    </svg>
);

function DeviceEndpointCellRenderer({ data, node }) {
    const isLeaf = node.level > 0;

    if (isLeaf) {
        return (
            <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                <span style={{ fontSize: 13, color: "#202223" }}>{data.endpoint}</span>
                <TypeBadge type={data.type} />
                {data.skillCount && <SkillBadge count={data.skillCount} />}
            </div>
        );
    }

    const childCount = node.childrenAfterGroup?.length ?? 0;

    return (
        <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <OsIcon os={data.os} />
            <span style={{ fontSize: 13, fontWeight: 600, color: "#202223" }}>{data.endpoint}</span>
            {childCount > 0 && (
                <span style={{
                    display: "inline-flex", alignItems: "center", justifyContent: "center",
                    minWidth: 20, height: 20, padding: "0 6px", borderRadius: 10,
                    fontSize: 11, fontWeight: 600,
                    background: "#F1F2F3", color: "#6D7175",
                }}>
                    {childCount}
                </span>
            )}
            {data.hasPersonalAccount && <UserPersonIcon color="#FCA5A5" />}
        </div>
    );
}

// ─── Users: username cell ────────────────────────────────────────────────────

const UserPersonIcon = ({ color = "#8C9196" }) => (
    <svg width="14" height="14" viewBox="0 0 20 20" fill={color} style={{ flexShrink: 0 }}>
        <path fillRule="evenodd" d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z" clipRule="evenodd"/>
    </svg>
);

function UsernameCellRenderer({ data }) {
    return (
        <div style={{ display: "flex", alignItems: "center", gap: 6, height: "100%" }}>
            <span style={{ fontSize: 13, fontWeight: 500, color: "#202223" }}>{data.username}</span>
            {data.hasPersonalAccount && <UserPersonIcon color="#FCA5A5" />}
        </div>
    );
}

// ─── Users: endpoints cell ────────────────────────────────────────────────────

function UserEndpointsCellRenderer({ data }) {
    const devices = devicesByUsername[data.username];
    if (!devices || devices.length === 0) return null;

    const primary = devices[0];
    const others = devices.slice(1);

    return (
        <div style={{ display: "flex", alignItems: "center", gap: 6, height: "100%" }}>
            <OsIcon os={primary.os} />
            <span style={{ fontSize: 13, color: "#202223" }}>{primary.endpoint}</span>
            {others.length > 0 && (
                <EndpointCountBadge count={others.length}>
                    {others.map((d, i) => (
                        <div key={i} style={{
                            display: "flex", alignItems: "center", gap: 8,
                            padding: "5px 12px",
                        }}>
                            <OsIcon os={d.os} />
                            <span style={{ fontSize: 12, color: "#202223" }}>{d.endpoint}</span>
                        </div>
                    ))}
                </EndpointCountBadge>
            )}
        </div>
    );
}

// ─── Column definitions ──────────────────────────────────────────────────────

const DEVICE_COL_DEFS = [
    // endpoint column is handled by autoGroupColumnDef in tree mode
    { field: "riskScore",   headerName: "Risk score",    width: 110,             cellRenderer: RiskScoreCellRenderer, sort: "desc" },
    { field: "username",    headerName: "Username",      flex: 1, minWidth: 120                                      },
    { field: "group",       headerName: "Group",         flex: 1, minWidth: 120                                      },
    { field: "role",        headerName: "Role",          flex: 1.2, minWidth: 150                                    },
    { field: "violations",  headerName: "Violations",    width: 160, sortable: false, cellRenderer: ViolationsCellRenderer },
    { field: "lastTraffic", headerName: "Last Traffic",  width: 130                                                  },
];

const USER_COL_DEFS = [
    {
        field: "username",
        headerName: "Username",
        flex: 1.5,
        minWidth: 180,
        pinned: "left",
        checkboxSelection: true,
        headerCheckboxSelection: true,
        cellRenderer: UsernameCellRenderer,
    },
    { field: "riskScore",  headerName: "Risk score", width: 110, cellRenderer: RiskScoreCellRenderer },
    {
        field: "userEndpoints",
        headerName: "Endpoints",
        flex: 1.8,
        minWidth: 200,
        sortable: false,
        cellRenderer: UserEndpointsCellRenderer,
    },
    { field: "violations", headerName: "Violations", width: 160, sortable: false, cellRenderer: ViolationsCellRenderer },
    { field: "group",      headerName: "Group",      flex: 1, minWidth: 130 },
    { field: "role",       headerName: "Role",       flex: 1.2, minWidth: 150 },
];

const DEFAULT_COL_DEF = {
    sortable: true,
    resizable: true,
    filter: true,
    cellStyle: { display: "flex", alignItems: "center" },
};

// ─── Table section ───────────────────────────────────────────────────────────

function BulkActionBar({ count, onClear }) {
    if (!count) return null;
    return (
        <div style={{
            display: "flex", alignItems: "center", gap: 10,
            padding: "8px 14px",
            background: "#F5F0FF",
            border: "1px solid #DDD3FA",
            borderRadius: 8,
            marginBottom: 10,
        }}>
            <span style={{
                display: "inline-flex", alignItems: "center", justifyContent: "center",
                minWidth: 24, height: 24, padding: "0 7px", borderRadius: 12,
                fontSize: 12, fontWeight: 700,
                background: "#7C3AED", color: "white",
            }}>
                {count}
            </span>
            <span style={{ fontSize: 13, color: "#4B5563", fontWeight: 500 }}>
                {count === 1 ? "row" : "rows"} selected
            </span>
            <div style={{ width: 1, height: 16, background: "#DDD3FA", margin: "0 2px" }} />
            <div style={{ display: "flex", gap: 8 }}>
                <button style={{
                    padding: "5px 14px", borderRadius: 6, cursor: "pointer",
                    fontSize: 13, fontWeight: 500,
                    background: "white", color: "#202223",
                    border: "1px solid #D1D5DB",
                    boxShadow: "0 1px 2px rgba(0,0,0,0.06)",
                }}>
                    Edit Team
                </button>
                <button style={{
                    padding: "5px 14px", borderRadius: 6, cursor: "pointer",
                    fontSize: 13, fontWeight: 500,
                    background: "white", color: "#202223",
                    border: "1px solid #D1D5DB",
                    boxShadow: "0 1px 2px rgba(0,0,0,0.06)",
                }}>
                    Edit Role
                </button>
            </div>
            <button
                onClick={onClear}
                style={{
                    marginLeft: "auto", background: "none", border: "none",
                    cursor: "pointer", color: "#6D7175", fontSize: 18, lineHeight: 1,
                    padding: "0 4px",
                }}
            >×</button>
        </div>
    );
}

function SearchBar({ value, onChange }) {
    return (
        <div style={{
            display: "flex", alignItems: "center", gap: 8,
            padding: "7px 12px",
            borderBottom: "1px solid #E1E3E5",
            borderRadius: "8px 8px 0 0",
            background: "white",
            flexShrink: 0,
        }}>
            <svg width="14" height="14" viewBox="0 0 20 20" fill="#8C9196" style={{ flexShrink: 0 }}>
                <path fillRule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clipRule="evenodd"/>
            </svg>
            <input
                type="text"
                placeholder="Search…"
                value={value}
                onChange={e => onChange(e.target.value)}
                style={{
                    flex: 1, border: "none", outline: "none",
                    fontSize: 13, color: "#202223",
                    background: "transparent",
                    fontFamily: "Inter, sans-serif",
                }}
            />
            {value && (
                <button onClick={() => onChange("")} style={{
                    background: "none", border: "none", cursor: "pointer",
                    color: "#8C9196", fontSize: 16, lineHeight: 1, padding: 0,
                }}>×</button>
            )}
        </div>
    );
}

function TableSection() {
    const [activeTab, setActiveTab] = useState("devices");
    const [deviceQuickFilter, setDeviceQuickFilter] = useState("");
    const [userQuickFilter, setUserQuickFilter] = useState("");
    const [selectedCount, setSelectedCount] = useState(0);
    const deviceGridRef = useRef(null);
    const userGridRef = useRef(null);
    const isDevices = activeTab === "devices";

    const getDataPath = useCallback((data) => data.path, []);

    const autoGroupColumnDef = useMemo(() => ({
        headerName: "Endpoint",
        flex: 2.5,
        minWidth: 300,
        pinned: "left",
        checkboxSelection: true,
        headerCheckboxSelection: true,
        cellRendererParams: {
            suppressCount: true,
            innerRenderer: DeviceEndpointCellRenderer,
        },
        cellStyle: { display: "flex", alignItems: "center" },
    }), []);

    return (
        <div>
            {/* Tab bar */}
            <div style={{
                display: "inline-flex",
                background: "#F1F2F3",
                borderRadius: 10,
                padding: 3,
                marginBottom: 14,
                gap: 2,
            }}>
                {[
                    { key: "devices", label: "Devices", count: 6403 },
                    { key: "users",   label: "Users",   count: 4203 },
                ].map(tab => (
                    <button
                        key={tab.key}
                        onClick={() => { setActiveTab(tab.key); setSelectedCount(0); }}
                        style={{
                            display: "flex", alignItems: "center", gap: 6,
                            padding: "5px 14px",
                            borderRadius: 8,
                            border: "none",
                            background: activeTab === tab.key ? "white" : "transparent",
                            boxShadow: activeTab === tab.key ? "0 1px 3px rgba(0,0,0,0.12), 0 1px 2px rgba(0,0,0,0.06)" : "none",
                            cursor: "pointer",
                            fontSize: 13,
                            fontWeight: activeTab === tab.key ? 600 : 400,
                            color: activeTab === tab.key ? "#202223" : "#6D7175",
                            fontFamily: "Inter, sans-serif",
                            outline: "none",
                            transition: "background 0.15s, box-shadow 0.15s",
                        }}
                    >
                        {tab.label}
                        <span style={{
                            fontSize: 11, fontWeight: 600,
                            color: activeTab === tab.key ? "#6D7175" : "#9CA3AF",
                        }}>
                            {tab.count.toLocaleString()}
                        </span>
                    </button>
                ))}
            </div>

            <BulkActionBar
                count={selectedCount}
                onClear={() => {
                    const ref = isDevices ? deviceGridRef : userGridRef;
                    ref.current?.api?.deselectAll();
                    setSelectedCount(0);
                }}
            />

            {/* Grid */}
            <div style={{
                height: selectedCount > 0 ? 754 : 800,
                border: "1px solid #E1E3E5",
                borderRadius: 8,
                display: "flex",
                flexDirection: "column",
            }}>
                {isDevices ? (
                    <>
                        <SearchBar value={deviceQuickFilter} onChange={setDeviceQuickFilter} />
                        <div style={{ flex: 1, minHeight: 0, borderRadius: "0 0 8px 8px", overflow: "hidden" }}>
                            <AgGridReact
                                key="devices"
                                ref={deviceGridRef}
                                theme={myThemeInner}
                                rowData={DEVICE_FLAT_DATA}
                                onSelectionChanged={e => setSelectedCount(e.api.getSelectedRows().length)}
                                columnDefs={DEVICE_COL_DEFS}
                                autoGroupColumnDef={autoGroupColumnDef}
                                defaultColDef={DEFAULT_COL_DEF}
                                treeData
                                getDataPath={getDataPath}
                                groupDefaultExpanded={0}
                                rowSelection="multiple"
                                suppressRowClickSelection
                                animateRows
                                rowHeight={44}
                                headerHeight={40}
                                suppressCellFocus
                                sideBar={{ toolPanels: ["columns", "filters"] }}
                                pagination
                                paginationPageSize={20}
                                paginationPageSizeSelector={[20, 50, 100]}
                                quickFilterText={deviceQuickFilter}
                            />
                        </div>
                    </>
                ) : (
                    <>
                        <SearchBar value={userQuickFilter} onChange={setUserQuickFilter} />
                        <div style={{ flex: 1, minHeight: 0, borderRadius: "0 0 8px 8px", overflow: "hidden" }}>
                            <AgGridReact
                                key="users"
                                ref={userGridRef}
                                theme={myThemeInner}
                                rowData={USER_FLAT_DATA}
                                onSelectionChanged={e => setSelectedCount(e.api.getSelectedRows().length)}
                                columnDefs={USER_COL_DEFS}
                                defaultColDef={DEFAULT_COL_DEF}
                                rowSelection="multiple"
                                suppressRowClickSelection
                                animateRows
                                rowHeight={44}
                                headerHeight={40}
                                suppressCellFocus
                                sideBar={{ toolPanels: ["columns", "filters"] }}
                                quickFilterText={userQuickFilter}
                                pagination
                                paginationPageSize={20}
                                paginationPageSizeSelector={[20, 50, 100]}
                            />
                        </div>
                    </>
                )}
            </div>
        </div>
    );
}

// ─── Main component ──────────────────────────────────────────────────────────

export default function DeviceEndpoints() {
    return (
        <PageWithMultipleCards
            title={
                <TitleWithInfo
                    tooltipContent="View all endpoints by device and user — track AI agent activity, risk scores, and violations."
                    titleText="Endpoints"
                    docsUrl="https://ai-security-docs.akto.io/agentic-ai-discovery/get-started"
                />
            }
            isFirstPage={true}
            components={[
                <TopSection key="top"/>,
                <TableSection key="table"/>,
            ]}
        />
    );
}
