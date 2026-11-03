import { useCallback, useEffect, useState } from "react";
import {
    ActionList, Box, Button, Card, Checkbox, EmptyState, HorizontalStack, IndexTable, Pagination, Popover, Text, VerticalStack,
} from "@shopify/polaris";
import { HorizontalDotsMinor } from "@shopify/polaris-icons";
import func from "@/util/func";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import SpinnerCentered from "../../../components/progress/SpinnerCentered";
import api from "../api";
import DestinationsCard from "./DestinationsCard";
import ResponseRuleModal from "./ResponseRuleModal";
import { PAGE_SIZE } from "./constants";
import transform from "./transform";

const HEADINGS = [{ title: "Active" }, { title: "Rule" }, { title: "Runs" }, { title: "Last run" }, { title: "" }];

function RuleMenu({ onEdit, onDuplicate, onDelete }) {
    const [open, setOpen] = useState(false);
    const close = () => setOpen(false);
    return (
        <Popover
            active={open}
            onClose={close}
            activator={<Button plain icon={HorizontalDotsMinor} accessibilityLabel="Rule actions" onClick={() => setOpen((current) => !current)} />}
        >
            <ActionList
                items={[
                    { content: "Edit", onAction: () => { close(); onEdit(); } },
                    { content: "Duplicate", onAction: () => { close(); onDuplicate(); } },
                    { content: "Delete", destructive: true, onAction: () => { close(); onDelete(); } },
                ]}
            />
        </Popover>
    );
}

function RuleRow({ rule, index, onToggle, onEdit, onDuplicate, onDelete }) {
    return (
        <IndexTable.Row id={rule.hexId} position={index}>
            <IndexTable.Cell>
                <Checkbox label={`Turn ${rule.name} on or off`} labelHidden checked={rule.active} onChange={(active) => onToggle(rule, active)} />
            </IndexTable.Cell>
            <IndexTable.Cell>
                <Box paddingBlockStart="2" paddingBlockEnd="2">
                    <VerticalStack gap="1">
                        <Text as="span" fontWeight="semibold">{rule.name}</Text>
                        <HorizontalStack gap="2" wrap={false} blockAlign="baseline">
                            <Box minWidth="40px"><Text as="span" variant="bodySm" fontWeight="semibold" color="subdued">WHEN</Text></Box>
                            <Text as="span" variant="bodySm">{transform.whenText(rule)}</Text>
                        </HorizontalStack>
                        <HorizontalStack gap="2" wrap={false} blockAlign="baseline">
                            <Box minWidth="40px"><Text as="span" variant="bodySm" fontWeight="semibold" color="subdued">THEN</Text></Box>
                            <Text as="span" variant="bodySm">{transform.thenText(rule)}</Text>
                        </HorizontalStack>
                    </VerticalStack>
                </Box>
            </IndexTable.Cell>
            <IndexTable.Cell>{transform.runsText(rule)}</IndexTable.Cell>
            <IndexTable.Cell><Text as="span" color="subdued">{transform.lastRunText(rule)}</Text></IndexTable.Cell>
            <IndexTable.Cell>
                <RuleMenu onEdit={() => onEdit(rule)} onDuplicate={() => onDuplicate(rule)} onDelete={() => onDelete(rule)} />
            </IndexTable.Cell>
        </IndexTable.Row>
    );
}

function ResponseRules() {
    const [rules, setRules] = useState([]);
    const [total, setTotal] = useState(0);
    const [page, setPage] = useState(0);
    const [loading, setLoading] = useState(true);
    const [destinations, setDestinations] = useState([]);
    const [modal, setModal] = useState({ open: false, form: transform.emptyForm() });

    const load = useCallback(async (nextPage) => {
        setLoading(true);
        try {
            const response = await api.fetchGuardrailResponseRules({ skip: nextPage * PAGE_SIZE, limit: PAGE_SIZE });
            const loaded = response?.rules || [];
            if (loaded.length === 0 && nextPage > 0) {
                setPage(nextPage - 1);
                return;
            }
            setRules(loaded);
            setTotal(response?.total || 0);
        } catch (e) {
            func.setToast(true, true, "Failed to load response rules");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { load(page); }, [page, load]);

    useEffect(() => {
        api.fetchResponseRuleDestinations()
            .then((response) => setDestinations(response?.destinations || []))
            .catch(() => func.setToast(true, true, "Failed to load destinations"));
    }, []);

    const openCreate = () => setModal({ open: true, form: transform.emptyForm() });
    const openEdit = (rule) => setModal({ open: true, form: transform.formFromRule(rule) });
    const openDuplicate = (rule) => setModal({ open: true, form: transform.formFromRule(rule, true) });
    const closeModal = () => setModal((current) => ({ ...current, open: false }));

    const onSaved = () => {
        closeModal();
        load(page);
    };

    const onToggle = async (rule, active) => {
        setRules((current) => current.map((entry) => (entry.hexId === rule.hexId ? { ...entry, active } : entry)));
        try {
            await api.toggleGuardrailResponseRule(rule.hexId, active);
        } catch (e) {
            setRules((current) => current.map((entry) => (entry.hexId === rule.hexId ? { ...entry, active: !active } : entry)));
            func.setToast(true, true, "Could not update the rule");
        }
    };

    const onDelete = (rule) => {
        func.showConfirmationModal(`Delete "${rule.name}"? This cannot be undone.`, "Delete", async () => {
            try {
                await api.deleteGuardrailResponseRules([rule.hexId]);
                func.setToast(true, false, "Response rule deleted");
                load(page);
            } catch (e) {
                func.setToast(true, true, "Could not delete the rule");
            }
        });
    };

    const first = total === 0 ? 0 : page * PAGE_SIZE + 1;
    const last = page * PAGE_SIZE + rules.length;
    const showEmpty = !loading && total === 0;

    return (
        <>
            <PageWithMultipleCards
                title="Response rules"
                subtitle="Assign, ticket, alert or ignore violations automatically as Atlas detects them"
                isFirstPage
                primaryAction={<Button primary onClick={openCreate}>Create rule</Button>}
                components={[
                    <Card key="rules" padding="0">
                        {loading && rules.length === 0 && <SpinnerCentered />}
                        {showEmpty && (
                            <EmptyState heading="No response rules yet" action={{ content: "Create rule", onAction: openCreate }} image="">
                                <p>Rules run when a violation matches their conditions, so the routine follow-up happens without anyone opening each one.</p>
                            </EmptyState>
                        )}
                        {rules.length > 0 && (
                            <>
                                <IndexTable resourceName={{ singular: "rule", plural: "rules" }} itemCount={rules.length} selectable={false} headings={HEADINGS} loading={loading}>
                                    {rules.map((rule, index) => (
                                        <RuleRow
                                            key={rule.hexId}
                                            rule={rule}
                                            index={index}
                                            onToggle={onToggle}
                                            onEdit={openEdit}
                                            onDuplicate={openDuplicate}
                                            onDelete={onDelete}
                                        />
                                    ))}
                                </IndexTable>
                                <Box padding="3">
                                    <HorizontalStack align="space-between" blockAlign="center">
                                        <Text as="span" color="subdued">{`${first}-${last} of ${total}`}</Text>
                                        <Pagination
                                            hasPrevious={page > 0}
                                            hasNext={last < total}
                                            onPrevious={() => setPage((current) => current - 1)}
                                            onNext={() => setPage((current) => current + 1)}
                                        />
                                    </HorizontalStack>
                                </Box>
                            </>
                        )}
                    </Card>,
                    <DestinationsCard key="destinations" destinations={destinations} />,
                ]}
            />
            <ResponseRuleModal open={modal.open} initialForm={modal.form} destinations={destinations} onClose={closeModal} onSaved={onSaved} />
        </>
    );
}

export default ResponseRules;
