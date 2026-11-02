package com.akto.dto.claude_identity;

import lombok.Getter;
import lombok.Setter;

/**
 * What cyborg knows about the Claude Desktop install on one device, as returned by
 * /fetchDeviceClaudeDesktopInfoMap keyed on device id.
 *
 * A wire DTO only — it is never stored. The durable form is {@link ClaudeDeviceIdentity}; this is
 * the flattened, already-resolved view the runtime needs to stamp traffic with an org.
 */
@Getter
@Setter
public class ClaudeDesktopInfo {

    /** Which Claude surface produced this row, e.g. "claude-desktop". */
    private String agentType;

    private String email;
    /** How the email was classified, e.g. "personal" / "corporate". */
    private String emailCategory;
    private String accountType;

    private String accountUuid;
    /** The org the device was last seen working in. This is what traffic gets attributed to. */
    private String organizationUuid;

    private Boolean loggedIn;

    /** Path the identity was read from, carried through for debugging a wrong attribution. */
    private String source;
}
