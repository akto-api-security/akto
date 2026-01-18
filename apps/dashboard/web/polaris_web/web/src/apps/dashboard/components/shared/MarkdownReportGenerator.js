
const MarkdownReportGenerator = {
    generateMarkdown: (data, type) => {
        const timestamp = new Date().toLocaleString();
        let content = `# Akto ${type} Report\n\n`;
        content += `> **Generated on:** ${timestamp}\n\n`;
        content += `---\n\n`;

        // Table of Contents
        content += `## Table of Contents\n\n`;
        content += `- [Executive Summary](#executive-summary)\n`;
        if (type === 'Issues') {
            content += `- [Severity Breakdown](#severity-breakdown)\n`;
        }
        content += `- [Detailed Findings](#detailed-findings)\n\n`;
        content += `---\n\n`;

        if (type === 'Issues') {
            content += MarkdownReportGenerator.generateIssuesContent(data);
        } else if (type === 'Test Run') {
            content += MarkdownReportGenerator.generateTestRunContent(data);
        }

        return content;
    },

    generateIssuesContent: (issues) => {
        let content = `## Executive Summary\n\n`;
        content += `**Total Issues**: ${issues.length}\n\n`;

        // Severity Breakdown
        const severityCount = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 };
        issues.forEach(issue => {
            const severity = (issue.severity?.[0] || issue.severityVal || 'UNKNOWN').toUpperCase();
            if (severityCount[severity] !== undefined) {
                severityCount[severity]++;
            } else {
                severityCount[severity] = (severityCount[severity] || 0) + 1;
            }
        });

        content += `### Severity Breakdown\n\n`;
        content += `| Severity | Count |\n| :--- | :---: |\n`;
        Object.entries(severityCount).forEach(([sev, count]) => {
            const badge = MarkdownReportGenerator.getSeverityBadge(sev);
            content += `| ${badge} | ${count} |\n`;
        });
        content += `\n---\n\n`;

        content += `## Detailed Findings\n\n`;

        issues.forEach((issue, index) => {
            const title = issue.issueName || issue.id?.testSubCategory || issue.name || `Issue #${index + 1}`;
            const severity = (issue.severity?.[0] || issue.severityVal || 'Unknown').toUpperCase();
            const url = issue.id?.apiInfoKey?.url || issue.url || 'N/A';
            const method = issue.id?.apiInfoKey?.method || issue.method || 'N/A';
            const badge = MarkdownReportGenerator.getSeverityBadge(severity);

            content += `### ${index + 1}. ${title} ${badge}\n\n`;

            content += `| Metadata | Value |\n| :--- | :--- |\n`;
            content += `| **Severity** | ${severity} |\n`;
            content += `| **Endpoint** | \`${method} ${url}\` |\n`;
            content += `| **Category** | ${issue.category || 'N/A'} |\n`;
            content += `| **Status** | ${issue.issueStatus ? issue.issueStatus.toUpperCase() : 'OPEN'} |\n`;
            if (issue.creationTime) {
                const dateStr = typeof issue.creationTime === 'number' ? new Date(issue.creationTime * 1000).toLocaleString() : issue.creationTime;
                content += `| **Discovered** | ${dateStr} |\n`;
            }
            content += `\n`;

            if (issue.description || issue.issueDescription) {
                content += `<details open>\n<summary><strong>Description</strong></summary>\n\n${issue.description || issue.issueDescription}\n</details>\n\n`;
            }

            if (issue.impact || issue.issueImpact) {
                content += `<details>\n<summary><strong>Impact</strong></summary>\n\n${issue.impact || issue.issueImpact}\n</details>\n\n`;
            }

            if (issue.remediation || issue.issueRemediation) {
                content += `<details>\n<summary><strong>Remediation</strong></summary>\n\n${issue.remediation || issue.issueRemediation}\n</details>\n\n`;
            }

            // References/Tags
            const cwe = issue.cwe || issue.cweDisplay || [];
            const cve = issue.cve || issue.cveDisplay || [];
            const tags = issue.tags || [];

            if (cwe.length > 0 || cve.length > 0 || tags.length > 0) {
                content += `#### References & Tags\n\n`;
                if (cwe.length > 0) content += `- **CWE**: ${cwe.join(', ')}\n`;
                if (cve.length > 0) content += `- **CVE**: ${cve.join(', ')}\n`;
                if (tags.length > 0) content += `- **Tags**: ${tags.join(', ')}\n`;
                content += `\n`;
            }

            content += `\n---\n\n`;
        });

        return content;
    },

    generateTestRunContent: (testRunData) => {
        let content = `## Executive Summary\n\n`;

        content += `| Metric | Value |\n| :--- | :--- |\n`;
        if (testRunData.name) content += `| **Name** | ${testRunData.name} |\n`;
        if (testRunData.total_severity) content += `| **Total Severity** | ${testRunData.total_severity} |\n`;
        if (testRunData.start) content += `| **Started** | ${new Date(testRunData.start * 1000).toLocaleString()} |\n`;
        if (testRunData.run_type) content += `| **Type** | ${testRunData.run_type} |\n`;
        content += `\n---\n\n`;

        content += `## Detailed Findings\n\n`;

        const results = testRunData.results || [];

        if (results.length === 0) {
            content += `*No results available.*\n`;
        } else {
            // Group by severity
            results.sort((a, b) => {
                const sevLevel = { 'CRITICAL': 0, 'HIGH': 1, 'MEDIUM': 2, 'LOW': 3, 'INFO': 4 };
                const aSev = (a.severity && a.severity.length > 0 ? a.severity[0] : 'INFO').toUpperCase();
                const bSev = (b.severity && b.severity.length > 0 ? b.severity[0] : 'INFO').toUpperCase();
                return (sevLevel[aSev] || 4) - (sevLevel[bSev] || 4);
            });

            results.forEach((result, index) => {
                const title = result.name || result.id?.testSubCategory || `Result #${index + 1}`;
                const severity = (result.severity && result.severity.length > 0 ? result.severity[0] : 'INFO').toUpperCase();
                const url = result.url || 'N/A';
                const badge = MarkdownReportGenerator.getSeverityBadge(severity);

                content += `### ${index + 1}. ${title} ${badge}\n\n`;

                content += `| Metadata | Value |\n| :--- | :--- |\n`;
                content += `| **Severity** | ${severity} |\n`;
                content += `| **Endpoint** | \`${url}\` |\n`;
                if (result.vulnerable !== undefined) {
                    content += `| **Vulnerable** | ${result.vulnerable ? 'Yes' : 'No'} |\n`;
                }
                content += `\n`;

                if (result.description) {
                    content += `<details open>\n<summary><strong>Description</strong></summary>\n\n${result.description}\n</details>\n\n`;
                }

                if (result.testResults && result.testResults.length > 0) {
                    content += `<details>\n<summary><strong>Test Executions</strong></summary>\n\n`;
                    result.testResults.forEach((exec, i) => {
                        content += `**Execution ${i + 1}**\n`;
                        if (exec.message) content += `- Message: \`${exec.message}\`\n`;
                        if (exec.errors && exec.errors.length > 0) content += `- Errors: ${exec.errors.join(', ')}\n`;
                        content += `\n`;
                    });
                    content += `</details>\n\n`;
                }

                content += `\n---\n\n`;
            });
        }

        return content;
    },

    getSeverityBadge: (severity) => {
        let color = 'lightgrey';
        let text = severity ? severity.toUpperCase() : 'UNKNOWN';

        switch (text) {
            case 'CRITICAL': color = 'red'; break;
            case 'HIGH': color = 'orange'; break;
            case 'MEDIUM': color = 'yellow'; break;
            case 'LOW': color = 'blue'; break;
            case 'INFO': color = 'lightgrey'; break;
        }

        // Return Shields.io badge markdown
        return `![${text}](https://img.shields.io/badge/-${text}-${color})`;
    }
};

export default MarkdownReportGenerator;
