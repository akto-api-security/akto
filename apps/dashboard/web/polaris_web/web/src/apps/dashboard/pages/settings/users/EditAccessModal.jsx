import { Banner, Box, Button, Checkbox, Divider, HorizontalStack, InlineError, Modal, Text, VerticalStack } from "@shopify/polaris"
import { useEffect, useRef, useState } from "react"
import SingleDate from "../../../components/layouts/SingleDate"
import Dropdown from "../../../components/layouts/Dropdown"
import settingRequests from "../api"
import func from "@/util/func"

// older records store display names; the dropdowns use role keys
const DISPLAY_NAME_TO_ROLE = { 'SECURITY ENGINEER': 'MEMBER', 'THREAT ENGINEER': 'THREAT_ENGINEER', 'THREAT VIEWER': 'THREAT_VIEWER' }
const roleKey = (role) => DISPLAY_NAME_TO_ROLE[(role || '').toUpperCase()] || role

// access expiry is stored as epoch seconds; it is edited as a local date and ends at the end of that day
const toDate = (epochSeconds) => epochSeconds ? new Date(epochSeconds * 1000) : null
const endOfDay = (date) => date ? Math.floor(new Date(date.getFullYear(), date.getMonth(), date.getDate(), 23, 59, 59).getTime() / 1000) : 0

/*
 * Edit a user's role in each product, and (admins only) when their access ends.
 * Products left unticked get no access. Users with only the older single role start with it in every product.
 */
function EditAccessModal({ user, productScopes, roleOptions, defaultRole, isAdmin, canRemove, isOnPrem, onClose, onSaved, onRemoved }) {
    const [mapping, setMapping] = useState({})
    const [expiresOn, setExpiresOn] = useState(null)
    const [saving, setSaving] = useState(false)
    const [step, setStep] = useState('edit') // edit, confirmRemove, confirmReset, resetLink
    const [resetLink, setResetLink] = useState('')
    const copyRef = useRef(null)

    useEffect(() => {
        if (!user) return
        const saved = user.scopeRoleMapping || {}
        const initial = {}
        if (Object.keys(saved).length > 0) {
            Object.entries(saved).forEach(([scope, role]) => { if (role && role !== 'NO_ACCESS') initial[scope] = roleKey(role) })
        } else if (user.role) {
            productScopes.forEach(scope => { initial[scope.value] = roleKey(user.role) })
        }
        setMapping(initial)
        setExpiresOn(toDate(user.accessExpiresAt))
        setStep('edit')
        setResetLink('')
    }, [user])

    // unticking a product is how access is taken away, so the dropdowns list only real roles
    const givable = roleOptions.filter(option => option.value !== 'NO_ACCESS')
    // plus the user's current role, so the dropdown never shows a blank value
    const optionsFor = (scope) => {
        const current = mapping[scope]
        if (!current || givable.some(option => option.value === current)) return givable
        return [...givable, { label: `${current} (you can't give this role)`, value: current, disabled: true }]
    }
    // a newly ticked product starts with the default invite role, never Admin by accident
    const firstGivable = (givable.find(option => option.value === defaultRole) || givable.find(option => option.value !== 'ADMIN') || givable[0])?.value

    const toggleScope = (scope, checked) => {
        setMapping(prev => {
            const next = { ...prev }
            if (checked) next[scope] = firstGivable
            else delete next[scope]
            return next
        })
    }

    const noProduct = Object.keys(mapping).length === 0
    const pastExpiry = expiresOn && endOfDay(expiresOn) <= Math.floor(Date.now() / 1000)
    const name = user?.name && user.name !== '-' ? user.name : user?.login

    const save = async () => {
        if (pastExpiry) return
        // products not shown here keep their roles; shown products left unticked get no access
        const toSave = { ...(user.scopeRoleMapping || {}) }
        productScopes.forEach(scope => { toSave[scope.value] = mapping[scope.value] || 'NO_ACCESS' })
        setSaving(true)
        try {
            // only admins set the expiry; the backend ignores it from anyone else
            await settingRequests.updateUserScopeRoleMapping(user.login, toSave, isAdmin ? endOfDay(expiresOn) : undefined)
            func.setToast(true, false, `Access updated for ${name}`)
            onSaved()
        } catch (e) {
            // the server's message is already shown
        } finally {
            setSaving(false)
        }
    }

    const remove = async () => {
        setSaving(true)
        try {
            await settingRequests.removeUser(user.login)
            func.setToast(true, false, `${name} removed from this account`)
            onRemoved()
        } catch (e) {
            setStep('edit')
        } finally {
            setSaving(false)
        }
    }

    const resetPassword = async () => {
        setSaving(true)
        try {
            const link = await settingRequests.resetUserPassword(user.login)
            setResetLink(link)
            setStep('resetLink')
        } catch (e) {
            setStep('edit')
        } finally {
            setSaving(false)
        }
    }

    const roleOptionsEmpty = givable.length === 0

    const editContent = (
        <VerticalStack gap="4">
            {roleOptionsEmpty ? (
                <Banner status="warning"><Text as="p">Your role can't give any roles yet. Ask an admin.</Text></Banner>
            ) : null}
            <VerticalStack gap="2">
                <Text variant="headingSm" as="h3">Products</Text>
                <Text variant="bodySm" color="subdued">Pick a role for each product. Products left unticked have no access.</Text>
            </VerticalStack>
            <VerticalStack gap="3">
                {productScopes.map((scope, index) => {
                    const checked = scope.value in mapping
                    return (
                        <VerticalStack gap="3" key={scope.value}>
                            {index > 0 ? <Divider /> : null}
                            <HorizontalStack align="space-between" blockAlign="center" gap="4" wrap={false}>
                                <Checkbox label={scope.label} checked={checked} disabled={roleOptionsEmpty && !checked}
                                    onChange={(value) => toggleScope(scope.value, value)} />
                                {checked ? (
                                    <Box minWidth="240px">
                                        <Dropdown id={`edit-role-${scope.value}`} menuItems={optionsFor(scope.value)}
                                            disabledOptions={optionsFor(scope.value).filter(option => option.disabled).map(option => option.value)}
                                            initial={mapping[scope.value]} selected={(role) => setMapping(prev => ({ ...prev, [scope.value]: role }))} />
                                    </Box>
                                ) : <Text color="subdued">No access</Text>}
                            </HorizontalStack>
                        </VerticalStack>
                    )
                })}
            </VerticalStack>
            {noProduct ? (
                <Banner status="warning"><Text as="p">{`${name} won't have access to any product.${canRemove ? ' To take them out of the account, remove the user instead.' : ''}`}</Text></Banner>
            ) : null}
            {isAdmin ? (
                <>
                    <Divider />
                    <VerticalStack gap="2">
                        <SingleDate
                            label="Access ends on (optional)"
                            dataKey="No end date"
                            data={expiresOn}
                            dispatch={(action) => setExpiresOn(Object.values(action.obj)[0])}
                            disableDatesBefore={new Date()}
                        />
                        <HorizontalStack align="space-between" blockAlign="center">
                            <Text variant="bodySm" color="subdued">After this day the user has no access to any product until you change it.</Text>
                            {expiresOn ? <Button plain onClick={() => setExpiresOn(null)}>Remove end date</Button> : null}
                        </HorizontalStack>
                        {pastExpiry ? <InlineError message="Pick today or a later day." fieldID="expiry" /> : null}
                    </VerticalStack>
                </>
            ) : null}
        </VerticalStack>
    )

    const secondaryActions = step === 'edit' ? [
        ...(canRemove ? [{ content: 'Remove user', destructive: true, onAction: () => setStep('confirmRemove') }] : []),
        ...(isAdmin && isOnPrem ? [{ content: 'Reset password', onAction: () => setStep('confirmReset') }] : []),
        { content: 'Cancel', onAction: onClose },
    ] : step === 'resetLink' ? [{ content: 'Done', onAction: onClose }] : [{ content: 'Back', onAction: () => setStep('edit') }]

    const primaryAction = {
        edit: { content: 'Save', onAction: save, loading: saving, disabled: roleOptionsEmpty },
        confirmRemove: { content: 'Remove user', destructive: true, onAction: remove, loading: saving },
        confirmReset: { content: 'Create reset link', onAction: resetPassword, loading: saving },
        resetLink: { content: 'Copy link', onAction: () => func.copyToClipboard(resetLink, copyRef, "Reset link copied") },
    }[step]

    return (
        <Modal
            open={!!user}
            onClose={onClose}
            title={step === 'confirmRemove' ? `Remove ${name}?` : step === 'confirmReset' || step === 'resetLink' ? `Reset password for ${name}` : `Edit access for ${name}`}
            primaryAction={primaryAction}
            secondaryActions={secondaryActions}
        >
            <Modal.Section>
                {step === 'edit' ? editContent : null}
                {step === 'confirmRemove' ? (
                    <Text>{`${user?.login} loses access to every product in this account. You can invite them again later.`}</Text>
                ) : null}
                {step === 'confirmReset' ? (
                    <Text>{`Create a one-time link that lets ${user?.login} set a new password. Send it to them yourself.`}</Text>
                ) : null}
                {step === 'resetLink' ? (
                    <VerticalStack gap="2">
                        <Text>Send this link to the user. It works once.</Text>
                        <Box padding="3" background="bg-subdued" borderRadius="2"><Text breakWord>{resetLink}</Text></Box>
                        <Box ref={copyRef} />
                    </VerticalStack>
                ) : null}
            </Modal.Section>
        </Modal>
    )
}

export default EditAccessModal
