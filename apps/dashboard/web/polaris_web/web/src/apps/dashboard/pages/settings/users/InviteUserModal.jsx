import { Banner, Box, Checkbox, Divider, Form, HorizontalStack, InlineError, Modal, Select, Text, TextField, VerticalStack } from "@shopify/polaris"
import { useEffect, useRef, useState } from "react"
import func from "@/util/func"
import settingRequests from "../api"
import CopyCommand from "../../../components/shared/CopyCommand"

const EMAIL_PATTERN = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/

/*
 * Invite someone with a role in each product. The current product is picked for them with the default invite role,
 * when the inviter can give it.
 */
const InviteUserModal = ({ open, onClose, productScopes, roleOptions, defaultInviteRole, currentProduct, onInvited }) => {
    const ref = useRef(null)
    const [email, setEmail] = useState('')
    const [mapping, setMapping] = useState({})
    const [triedSend, setTriedSend] = useState(false)
    const [sending, setSending] = useState(false)
    const [inviteLink, setInviteLink] = useState('')

    const givable = roleOptions.filter(option => option.value !== 'NO_ACCESS')
    const startingRole = givable.some(option => option.value === defaultInviteRole) ? defaultInviteRole : givable[0]?.value

    useEffect(() => {
        if (open) {
            setEmail('')
            setTriedSend(false)
            setInviteLink('')
            const product = productScopes.find(scope => scope.value === currentProduct) || productScopes[0]
            setMapping(product && startingRole ? { [product.value]: startingRole } : {})
        }
    }, [open])

    const emailError = email.trim().length === 0 ? 'Enter an email address.' : !EMAIL_PATTERN.test(email.trim()) ? 'Enter a valid email address.' : ''
    const noProduct = Object.keys(mapping).length === 0

    const send = async () => {
        setTriedSend(true)
        if (emailError || noProduct) return
        setSending(true)
        try {
            const response = await settingRequests.inviteUsers({
                inviteeName: "there",
                inviteeEmail: email.trim().toLowerCase(),
                websiteHostName: window.location.origin,
                scopeRoleMapping: mapping
            })
            setInviteLink(response?.finalInviteCode || '')
            func.setToast(true, false, `Invite sent to ${email.trim().toLowerCase()}`)
            onInvited()
        } catch (e) {
            // the server's message is already shown
        } finally {
            setSending(false)
        }
    }

    if (inviteLink) {
        return (
            <Modal open={open} onClose={onClose} title="Invite sent"
                primaryAction={{ content: 'Copy link', onAction: () => func.copyToClipboard(inviteLink, ref, "Invite link copied") }}
                secondaryActions={[{ content: 'Done', onAction: onClose }]}>
                <Modal.Section>
                    <VerticalStack gap="3">
                        <Text>We emailed the invite. You can also share this link with them directly. It works for one week.</Text>
                        <CopyCommand command={inviteLink} />
                        <Box ref={ref} />
                    </VerticalStack>
                </Modal.Section>
            </Modal>
        )
    }

    return (
        <Modal
            open={open}
            onClose={onClose}
            title="Invite user"
            primaryAction={{ content: 'Send invite', onAction: send, loading: sending, disabled: givable.length === 0 }}
            secondaryActions={[{ content: 'Cancel', onAction: onClose }]}
        >
            <Modal.Section>
                <Form onSubmit={send}>
                    <VerticalStack gap="4">
                        {givable.length === 0 ? (
                            <Banner status="warning"><Text as="p">Your role can't give any roles yet. Ask an admin.</Text></Banner>
                        ) : null}
                        <TextField
                            label="Email"
                            type="email"
                            value={email}
                            placeholder="name@company.com"
                            onChange={setEmail}
                            helpText="We'll send the invite to this address."
                            error={triedSend && emailError ? emailError : undefined}
                            autoComplete="off"
                            autoFocus
                        />
                        <VerticalStack gap="2">
                            <Text variant="headingSm" as="h3">Products</Text>
                            <Text variant="bodySm" color="subdued">Pick a role for each product they need. Other products have no access.</Text>
                        </VerticalStack>
                        <VerticalStack gap="3">
                            {productScopes.map((scope, index) => {
                                const checked = scope.value in mapping
                                return (
                                    <VerticalStack gap="3" key={scope.value}>
                                        {index > 0 ? <Divider /> : null}
                                        <HorizontalStack align="space-between" blockAlign="center" gap="4" wrap={false}>
                                            <Checkbox label={scope.label} checked={checked} disabled={givable.length === 0}
                                                onChange={(value) => setMapping(prev => {
                                                    const next = { ...prev }
                                                    if (value) next[scope.value] = startingRole
                                                    else delete next[scope.value]
                                                    return next
                                                })} />
                                            {checked ? (
                                                <Box minWidth="240px">
                                                    <Select label={`Role in ${scope.label}`} labelHidden options={givable} value={mapping[scope.value]}
                                                        onChange={(role) => setMapping(prev => ({ ...prev, [scope.value]: role }))} />
                                                </Box>
                                            ) : <Text color="subdued">No access</Text>}
                                        </HorizontalStack>
                                    </VerticalStack>
                                )
                            })}
                        </VerticalStack>
                        {triedSend && noProduct ? <InlineError message="Pick at least one product." fieldID="invite-products" /> : null}
                    </VerticalStack>
                </Form>
            </Modal.Section>
        </Modal>
    )
}

export default InviteUserModal
