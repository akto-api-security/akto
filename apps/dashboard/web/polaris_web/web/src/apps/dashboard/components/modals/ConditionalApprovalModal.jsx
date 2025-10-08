import React, { useState, useEffect } from "react";
import {
    Modal,
    FormLayout,
    TextField,
    Select,
    Checkbox,
    Button,
    LegacyStack,
    Text,
    LegacyCard,
    HorizontalStack,
    RadioButton
} from "@shopify/polaris";
import settingRequests from "../../pages/settings/api";
import PersistStore from "../../../main/PersistStore";
import UpdateIpsComponent from "../shared/UpdateIpsComponent";
import DropdownSearch from "../shared/DropdownSearch";

const ConditionalApprovalModal = ({ 
    isOpen, 
    onClose, 
    onApprove, 
    auditItem 
}) => {
    // Time restriction state
    const [timeRestricted, setTimeRestricted] = useState(false);
    const [timeOption, setTimeOption] = useState("1_hour");
    const [customHours, setCustomHours] = useState("");

    // IP restriction state
    const [ipRestricted, setIpRestricted] = useState(false);
    const [ipOption, setIpOption] = useState("all_ips");
    const [specificIpsList, setSpecificIpsList] = useState([]);
    const [ipRangeList, setIpRangeList] = useState([]);

    // User restriction state
    const [userRestricted, setUserRestricted] = useState(false);
    const [userOption, setUserOption] = useState("all_users");
    const [selectedUsers, setSelectedUsers] = useState([]);
    const [availableUsers, setAvailableUsers] = useState([]);

    // Justification
    const [justification, setJustification] = useState("");

    // Loading state
    const [loading, setLoading] = useState(false);

    // Time options
    const timeOptions = [
        { label: "1 hour", value: "1_hour" },
        { label: "4 hours", value: "4_hours" },
        { label: "24 hours", value: "24_hours" },
        { label: "7 days", value: "7_days" },
        { label: "30 days", value: "30_days" },
        { label: "Custom", value: "custom" }
    ];

    // Fetch available users
    useEffect(() => {
        const fetchUsers = async () => {
            try {
                const response = await settingRequests.getTeamData();
                if (response) {
                    setAvailableUsers(response.map(user => ({
                        value: user.login,
                        label: user.login
                    })));
                }
            } catch (error) {
                console.error("Error fetching team data:", error);
            }
        };
        
        if (isOpen) {
            fetchUsers();
        }
    }, [isOpen]);

    // Reset form when modal opens/closes
    useEffect(() => {
        if (!isOpen) {
            resetForm();
        }
    }, [isOpen]);

    // Handle IP/CIDR changes similar to settings page
    const handleIpsChange = (ip, isAdded, type) => {
        let ipList = ip.split(",");
        ipList = ipList.map((x) => x.replace(/\s+/g, ''));
        
        if (type === 'ip') {
            let updatedIps = [];
            if (isAdded) {
                updatedIps = [...specificIpsList, ...ipList];
            } else {
                updatedIps = specificIpsList.filter(item => item !== ip);
            }
            updatedIps = Array.from(new Set(updatedIps));
            setSpecificIpsList(updatedIps);
        } else if (type === 'cidr') {
            let updatedIps = [];
            if (isAdded) {
                updatedIps = [...ipRangeList, ...ipList];
            } else {
                updatedIps = ipRangeList.filter(item => item !== ip);
            }
            updatedIps = Array.from(new Set(updatedIps));
            setIpRangeList(updatedIps);
        }
    };

    const resetForm = () => {
        setTimeRestricted(false);
        setTimeOption("1_hour");
        setCustomHours("");
        setIpRestricted(false);
        setIpOption("all_ips");
        setSpecificIpsList([]);
        setIpRangeList([]);
        setUserRestricted(false);
        setUserOption("all_users");
        setSelectedUsers([]);
        setJustification("");
    };

    const handleApprove = async () => {
        if (!justification.trim()) {
            alert("Justification is required");
            return;
        }

        setLoading(true);

        // Build approval conditions
        const conditions = {};

        // Time conditions
        if (timeRestricted) {
            if (timeOption === "custom") {
                if (!customHours || parseInt(customHours) <= 0) {
                    alert("Please enter valid custom hours");
                    setLoading(false);
                    return;
                }
                conditions.expiresInHours = parseInt(customHours);
            } else {
                const hoursMap = {
                    "1_hour": 1,
                    "4_hours": 4,
                    "24_hours": 24,
                    "7_days": 168,
                    "30_days": 720
                };
                conditions.expiresInHours = hoursMap[timeOption];
            }
            const currentTimeSeconds = Math.floor(Date.now() / 1000);
            const durationSeconds = conditions.expiresInHours * 60 * 60;
            conditions.expiresAt = Math.floor(currentTimeSeconds + durationSeconds);
        }

        // IP conditions
        if (ipRestricted) {
            if (ipOption === "specific_ips") {
                if (!specificIpsList || specificIpsList.length === 0) {
                    alert("Please add at least one specific IP address");
                    setLoading(false);
                    return;
                }
                conditions.allowedIps = specificIpsList;
            } else if (ipOption === "ip_range") {
                if (!ipRangeList || ipRangeList.length === 0) {
                    alert("Please add at least one IP range in CIDR notation");
                    setLoading(false);
                    return;
                }
                conditions.allowedIpRange = ipRangeList;
            }
        }

        // User conditions
        if (userRestricted && userOption === "specific_users") {
            if (selectedUsers.length === 0) {
                alert("Please select at least one user");
                setLoading(false);
                return;
            }
            conditions.allowedUsers = selectedUsers;
        }

        try {
            await onApprove(auditItem.hexId, {
                remarks: "Conditionally Approved",
                conditions: conditions,
                justification: justification.trim()
            });
            onClose();
        } catch (error) {
            console.error("Error approving with conditions:", error);
            alert("Error occurred while approving. Please try again.");
        } finally {
            setLoading(false);
        }
    };


    return (
        <Modal
            open={isOpen}
            onClose={onClose}
            title="Conditional Approval"
            primaryAction={{
                content: "Approve with Conditions",
                onAction: handleApprove,
                loading: loading,
                disabled: !justification.trim()
            }}
            secondaryActions={[
                {
                    content: "Cancel",
                    onAction: onClose
                }
            ]}
            large
        >
            <Modal.Section>
                <LegacyStack vertical spacing="loose">
                    {/* Resource Information */}
                    <LegacyCard sectioned>
                        <LegacyStack vertical spacing="tight">
                            <Text variant="headingMd">Resource Information</Text>
                            <Text><strong>Resource Name:</strong> {auditItem?.resourceName || "N/A"}</Text>
                            <Text><strong>Type:</strong> {auditItem?.type || "N/A"}</Text>
                            <Text><strong>Collection:</strong> {auditItem?.collectionName || "N/A"}</Text>
                        </LegacyStack>
                    </LegacyCard>

                    {/* Approval Conditions */}
                    <LegacyCard sectioned>
                        <LegacyStack vertical spacing="loose">
                            <Text variant="headingMd">Approval Conditions</Text>

                            {/* Time Restriction */}
                            <FormLayout>
                                <Checkbox
                                    label="Time Restriction"
                                    checked={timeRestricted}
                                    onChange={setTimeRestricted}
                                />
                                {timeRestricted && (
                                    <LegacyCard sectioned>
                                        <LegacyStack vertical spacing="tight">
                                            <DropdownSearch
                                                label="Duration"
                                                placeholder="Select duration"
                                                optionsList={timeOptions}
                                                setSelected={setTimeOption}
                                                value={timeOptions.find(opt => opt.value === timeOption)?.label}
                                                searchDisable={true}
                                            />
                                            {timeOption === "custom" && (
                                                <TextField
                                                    label="Custom hours"
                                                    type="number"
                                                    value={customHours}
                                                    onChange={setCustomHours}
                                                    placeholder="Enter number of hours"
                                                />
                                            )}
                                        </LegacyStack>
                                    </LegacyCard>
                                )}

                                {/* IP Restriction */}
                                <Checkbox
                                    label="IP Restriction"
                                    checked={ipRestricted}
                                    onChange={setIpRestricted}
                                />
                                {ipRestricted && (
                                    <LegacyCard sectioned>
                                        <LegacyStack vertical spacing="tight">
                                            <LegacyStack vertical spacing="tight">
                                                <RadioButton
                                                    label="All IPs"
                                                    checked={ipOption === "all_ips"}
                                                    id="all_ips"
                                                    name="ipOption"
                                                    onChange={() => setIpOption("all_ips")}
                                                />
                                                <RadioButton
                                                    label="Specific IPs"
                                                    checked={ipOption === "specific_ips"}
                                                    id="specific_ips"
                                                    name="ipOption"
                                                    onChange={() => setIpOption("specific_ips")}
                                                />
                                                {ipOption === "specific_ips" && (
                                                    <UpdateIpsComponent
                                                        labelText="Add IP"
                                                        ipsList={specificIpsList}
                                                        onSubmit={(val) => handleIpsChange(val, true, "ip")}
                                                        onRemove={(val) => handleIpsChange(val, false, "ip")}
                                                        type="ip"
                                                        showCard={false}
                                                        showTitle={false}
                                                    />
                                                )}
                                                <RadioButton
                                                    label="IP Range (CIDR)"
                                                    checked={ipOption === "ip_range"}
                                                    id="ip_range"
                                                    name="ipOption"
                                                    onChange={() => setIpOption("ip_range")}
                                                />
                                                {ipOption === "ip_range" && (
                                                    <UpdateIpsComponent
                                                        labelText="Add CIDR"
                                                        ipsList={ipRangeList}
                                                        onSubmit={(val) => handleIpsChange(val, true, "cidr")}
                                                        onRemove={(val) => handleIpsChange(val, false, "cidr")}
                                                        type="cidr"
                                                        showCard={false}
                                                        showTitle={false}
                                                    />
                                                )}
                                            </LegacyStack>
                                        </LegacyStack>
                                    </LegacyCard>
                                )}

                                {/* User Restriction */}
                                <Checkbox
                                    label="User Restriction"
                                    checked={userRestricted}
                                    onChange={setUserRestricted}
                                />
                                {userRestricted && (
                                    <LegacyCard sectioned>
                                        <LegacyStack vertical spacing="tight">
                                            <RadioButton
                                                label="All users"
                                                checked={userOption === "all_users"}
                                                id="all_users"
                                                name="userOption"
                                                onChange={() => setUserOption("all_users")}
                                            />
                                            <RadioButton
                                                label="Specific users"
                                                checked={userOption === "specific_users"}
                                                id="specific_users"
                                                name="userOption"
                                                onChange={() => setUserOption("specific_users")}
                                            />
                                            {userOption === "specific_users" && (
                                                <LegacyStack vertical spacing="tight">
                                                    <DropdownSearch
                                                        label="Select users"
                                                        placeholder="Select users"
                                                        optionsList={availableUsers}
                                                        setSelected={setSelectedUsers}
                                                        preSelected={selectedUsers}
                                                        allowMultiple={true}
                                                        itemName="user"
                                                        value={`${selectedUsers.length} user${selectedUsers.length !== 1 ? 's' : ''} selected`}
                                                    />
                                                </LegacyStack>
                                            )}
                                        </LegacyStack>
                                    </LegacyCard>
                                )}

                                {/* Justification */}
                                <TextField
                                    label="Justification (Required)"
                                    value={justification}
                                    onChange={setJustification}
                                    multiline={4}
                                    placeholder="Provide a detailed justification for this approval..."
                                    requiredIndicator
                                />
                            </FormLayout>
                        </LegacyStack>
                    </LegacyCard>
                </LegacyStack>
            </Modal.Section>
        </Modal>
    );
};

export default ConditionalApprovalModal;