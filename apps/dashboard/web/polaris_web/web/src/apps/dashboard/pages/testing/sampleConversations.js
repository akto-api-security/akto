export const sampleConversations = [
    {
        _id: "507f1f77bcf86cd799439011",
        creationTimestamp: 1672531200,
        testRunResultId: "68a422a9789aff18847a053f",
        message: "Analyze the following API endpoint for potential security vulnerabilities",
        conversationId: "507f1f77bcf86cd799439013",
        role: "user"
    },
    {
        _id: "507f1f77bcf86cd799439014",
        creationTimestamp: 1672531260,
        testRunResultId: "68a422a9789aff18847a053f",
        message: `I've identified several potential security issues with this endpoint. The main concerns are:

1. **SQL injection vulnerability** in the user_id parameter
2. **Missing authentication validation**
3. **Lack of input sanitization**`,
        conversationId: "507f1f77bcf86cd799439013",
        role: "system"
    },
    {
        _id: "507f1f77bcf86cd799439015",
        creationTimestamp: 1672531320,
        testRunResultId: "68a422a9789aff18847a053f",
        message: "Can you provide specific remediation steps for the SQL injection vulnerability?",
        conversationId: "507f1f77bcf86cd799439013",
        role: "user"
    },
    {
        _id: "507f1f77bcf86cd799439016",
        creationTimestamp: 1672531380,
        testRunResultId: "68a422a9789aff18847a053f",
        message: `To fix the **SQL injection vulnerability**:

1. **Use parameterized queries** or prepared statements
2. **Implement proper input validation** and sanitization
3. **Use an ORM** with built-in protection
4. **Apply the principle of least privilege** for database access`,
        conversationId: "507f1f77bcf86cd799439013",
        role: "system"
    },
    {
        _id: "507f1f77bcf86cd799439025",
        creationTimestamp: 1672531440,
        testRunResultId: "68a422a9789aff18847a053f",
        message: "What about the missing authentication issue? How critical is that?",
        conversationId: "507f1f77bcf86cd799439013",
        role: "user"
    },
    {
        _id: "507f1f77bcf86cd799439026",
        creationTimestamp: 1672531500,
        testRunResultId: "68a422a9789aff18847a053f",
        message: `The **missing authentication** is critically severe. This endpoint exposes sensitive user data without any verification.

### Immediate steps:
1. **Add JWT token validation** middleware
2. **Implement role-based access control**
3. **Log all access attempts** for monitoring`,
        conversationId: "507f1f77bcf86cd799439013",
        role: "system"
    },
    {
        _id: "507f1f77bcf86cd799439027",
        creationTimestamp: 1672531560,
        testRunResultId: "68a422a9789aff18847a053f",
        message: "Thanks! Can you also check if there are any rate limiting issues?",
        conversationId: "507f1f77bcf86cd799439013",
        role: "user"
    },
    {
        _id: "507f1f77bcf86cd799439028",
        creationTimestamp: 1672531620,
        testRunResultId: "68a422a9789aff18847a053f",
        message: `Yes, this endpoint lacks **rate limiting** which makes it vulnerable to brute force attacks and DoS.

### Recommendations:
1. **Implement request throttling** (max 100 requests/minute per IP)
2. **Add progressive delays** for repeated requests
3. **Use Redis** for distributed rate limiting in clustered environments`,
        conversationId: "507f1f77bcf86cd799439013",
        role: "system"
    },
    {
        _id: "507f1f77bcf86cd799439029",
        creationTimestamp: 1672531680,
        testRunResultId: "68a422a9789aff18847a053f",
        message: "Perfect! This analysis is very helpful. Should I prioritize these fixes in any particular order?",
        conversationId: "507f1f77bcf86cd799439013",
        role: "user"
    },
    {
        _id: "507f1f77bcf86cd799439030",
        creationTimestamp: 1672531740,
        testRunResultId: "68a422a9789aff18847a053f",
        message: `### Priority order:

1. **\uD83D\uDD34 Fix authentication** (Critical - immediate data exposure risk)
2. **\uD83D\uDFE0 SQL injection** (High - potential data breach)
3. **\uD83D\uDFE1 Rate limiting** (Medium - DoS protection)
4. **\uD83D\uDFE1 Input sanitization** (Medium - prevents various attacks)

> **Focus on authentication first** as it's the most severe vulnerability.`,
        conversationId: "507f1f77bcf86cd799439013",
        role: "system"
    },
    {
        _id: "507f1f77bcf86cd799439017",
        creationTimestamp: 1672531440,
        testRunResultId: "507f1f77bcf86cd799439018",
        message: "Check this authentication bypass vulnerability",
        conversationId: "507f1f77bcf86cd799439019",
        role: "user"
    },
    {
        _id: "507f1f77bcf86cd799439020",
        creationTimestamp: 1672531500,
        testRunResultId: "507f1f77bcf86cd799439018",
        message: `This endpoint allows **unauthorized access** by bypassing JWT token validation.

The vulnerability occurs when the token verification logic **fails silently** instead of rejecting invalid tokens.`,
        conversationId: "507f1f77bcf86cd799439019",
        role: "system"
    },
    {
        _id: "507f1f77bcf86cd799439021",
        creationTimestamp: 1672531560,
        testRunResultId: "507f1f77bcf86cd799439022",
        message: "Evaluate the XSS potential in the user input fields",
        conversationId: "507f1f77bcf86cd799439023",
        role: "user"
    },
    {
        _id: "507f1f77bcf86cd799439024",
        creationTimestamp: 1672531620,
        testRunResultId: "507f1f77bcf86cd799439022",
        message: `The application is vulnerable to **stored XSS attacks**.

User input is being directly inserted into the DOM without proper encoding or sanitization. This allows **malicious scripts to be executed** in other users' browsers.

### Risk:
- Cookie theft
- Session hijacking
- Data exfiltration`,
        conversationId: "507f1f77bcf86cd799439023",
        role: "system"
    }
]

// Available models for the chat interface
export const availableModels = [
    { id: 'claude-3-sonnet', name: 'Claude-3.5-sonnet' },
    { id: 'gpt-4o', name: 'GPT-4o' },
    { id: 'gpt-4o-mini', name: 'GPT-4o-mini' },
    { id: 'gpt-3.5-turbo', name: 'GPT-3.5-turbo' },
    { id: 'gemini-1.5-flash', name: 'Gemini-1.5-flash' }
]