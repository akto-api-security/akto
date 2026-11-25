import { Button, ChoiceList, HorizontalStack, Text, TextField, VerticalStack } from '@shopify/polaris'
import { useEffect, useState } from 'react'
import PasswordTextField from '../../../components/layouts/PasswordTextField'
import api from '../api'
import func from '@/util/func'

const AUTH_ROLE = 'ROLE'
const AUTH_ACCESS_KEYS = 'ACCESS_KEYS'

function AtlasS3ExportConnector() {
    const [bucketName, setBucketName] = useState('')
    const [region, setRegion] = useState('')
    const [prefix, setPrefix] = useState('')
    const [authType, setAuthType] = useState(AUTH_ROLE)
    const [roleArn, setRoleArn] = useState('')
    const [accessKeyId, setAccessKeyId] = useState('')
    const [secretAccessKey, setSecretAccessKey] = useState('')
    // Access key ID whose secret is already saved; the secret itself is never sent to the browser.
    const [savedAccessKeyId, setSavedAccessKeyId] = useState('')
    const [isSaved, setIsSaved] = useState(false)
    const [loading, setLoading] = useState(false)

    const resetFields = () => {
        setBucketName(''); setRegion(''); setPrefix(''); setAuthType(AUTH_ROLE); setRoleArn('')
        setAccessKeyId(''); setSecretAccessKey(''); setSavedAccessKeyId(''); setIsSaved(false)
    }

    useEffect(() => {
        api.fetchAtlasS3ExportIntegration().then((res) => {
            const integration = res?.atlasS3ExportIntegration
            if (integration) {
                setBucketName(integration.bucketName || '')
                setRegion(integration.region || '')
                setPrefix(integration.prefix || '')
                setAuthType(integration.authType === AUTH_ACCESS_KEYS ? AUTH_ACCESS_KEYS : AUTH_ROLE)
                setRoleArn(integration.roleArn || '')
                setAccessKeyId(integration.accessKeyId || '')
                setSavedAccessKeyId(integration.authType === AUTH_ACCESS_KEYS ? (integration.accessKeyId || '') : '')
                setIsSaved(true)
            }
        }).catch(() => {})
    }, [])

    const usesKeys = authType === AUTH_ACCESS_KEYS
    // A blank secret keeps the saved one, but only while the access key ID is unchanged.
    const canKeepSavedSecret = usesKeys && savedAccessKeyId !== '' && accessKeyId.trim() === savedAccessKeyId
    const authReady = usesKeys
        ? accessKeyId.trim() !== '' && (secretAccessKey.trim() !== '' || canKeepSavedSecret)
        : roleArn.trim() !== ''

    const handleSave = () => {
        setLoading(true)
        api.addAtlasS3ExportIntegration(bucketName, region, prefix, authType,
            usesKeys ? '' : roleArn, usesKeys ? accessKeyId : '', usesKeys ? secretAccessKey : '').then(() => {
            setIsSaved(true)
            setSavedAccessKeyId(usesKeys ? accessKeyId.trim() : '')
            setSecretAccessKey('')
            func.setToast(true, false, 'Atlas S3 export saved successfully')
        }).catch((err) => {
            func.setToast(true, true, err?.response?.data?.actionErrors?.[0] || 'Failed to save Atlas S3 export')
        }).finally(() => setLoading(false))
    }

    const handleRemove = () => {
        setLoading(true)
        api.removeAtlasS3ExportIntegration().then(() => {
            resetFields()
            func.setToast(true, false, 'Atlas S3 export removed successfully')
        }).catch(() => func.setToast(true, true, 'Failed to remove Atlas S3 export'))
            .finally(() => setLoading(false))
    }

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Export traces (guardrail hits, models, token usage, prompts and responses) in OpenTelemetry (OTLP JSON, gzip) format to your own S3 bucket.
            </Text>
            <VerticalStack gap="2">
                <TextField label="S3 Bucket Name" value={bucketName} onChange={setBucketName} placeholder="my-atlas-traces" requiredIndicator autoComplete="off" />
                <TextField label="AWS Region" value={region} onChange={setRegion} placeholder="us-east-1" requiredIndicator autoComplete="off" />
                <TextField label="Key Prefix" value={prefix} onChange={setPrefix} placeholder="akto/atlas" helpText="Optional. Files are written under <prefix>/year=YYYY/month=MM/day=DD/hour=HH/minute=mm/" autoComplete="off" />
                <ChoiceList
                    title="Authentication"
                    choices={[
                        { label: 'IAM role (recommended)', value: AUTH_ROLE },
                        { label: 'Access keys', value: AUTH_ACCESS_KEYS },
                    ]}
                    selected={[authType]}
                    onChange={(selected) => setAuthType(selected[0])}
                />
                {usesKeys ? (
                    <>
                        <Text variant='bodyMd' color='subdued'>
                            Create an IAM user in your AWS account that can only <code>s3:PutObject</code> on your bucket (plus <code>kms:GenerateDataKey</code> if the bucket uses SSE-KMS), and enter its access keys.
                        </Text>
                        <TextField label="Access Key ID" value={accessKeyId} onChange={setAccessKeyId} placeholder="AKIA..." requiredIndicator autoComplete="off" />
                        <PasswordTextField label="Secret Access Key" field={secretAccessKey} setField={setSecretAccessKey} onFunc={true}
                            requiredIndicator helpText={canKeepSavedSecret ? 'Saved. Leave blank to keep the current secret.' : undefined} />
                    </>
                ) : (
                    <>
                        <Text variant='bodyMd' color='subdued'>
                            Create an IAM role in your AWS account that trusts Akto's AWS account (shared via your help desk ticket) with External ID <b>{window.ACTIVE_ACCOUNT}</b>, and allow it <code>s3:PutObject</code> on your bucket (plus <code>kms:GenerateDataKey</code> if the bucket uses SSE-KMS).
                        </Text>
                        <TextField label="IAM Role ARN" value={roleArn} onChange={setRoleArn} placeholder="arn:aws:iam::123456789012:role/akto-atlas-export" helpText="The role Akto assumes to write to your bucket." requiredIndicator autoComplete="off" />
                    </>
                )}
            </VerticalStack>
            <HorizontalStack align='end' gap="2">
                {isSaved ? <Button onClick={handleRemove} disabled={loading} destructive>Remove</Button> : null}
                <Button onClick={handleSave} disabled={loading || !bucketName.trim() || !region.trim() || !authReady} loading={loading} primary>Save</Button>
            </HorizontalStack>
        </div>
    )
}

export default AtlasS3ExportConnector
