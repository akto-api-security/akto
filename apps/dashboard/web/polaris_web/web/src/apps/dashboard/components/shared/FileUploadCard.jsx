import { HorizontalStack, Text, Badge, VerticalStack, ButtonGroup, Button } from "@shopify/polaris";
import { CancelMajor } from "@shopify/polaris-icons";
import FileUpload from "./FileUpload";

function FileUploadCard({
    description,
    files,
    onFileRemove,
    acceptString,
    setSelectedFile,
    allowMultiple,
    allowedSize,
    onUpload,
    loading,
    onSecondaryAction,
    secondaryActionLabel = "Go to docs"
}) {
    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                {description}
            </Text>

            <HorizontalStack gap="2">
                {files ?
                    <Badge size='medium' status='success'>
                        {files.name}
                        <Button icon={CancelMajor} plain onClick={onFileRemove} />
                    </Badge>
                    : null}
                File: <FileUpload
                    fileType="file"
                    acceptString={acceptString}
                    setSelectedFile={setSelectedFile}
                    allowMultiple={allowMultiple}
                    allowedSize={allowedSize} />
            </HorizontalStack>

            <VerticalStack gap="2">
                <ButtonGroup>
                    <Button
                        onClick={onUpload}
                        primary
                        disabled={files === null}
                        loading={loading}>
                        Upload
                    </Button>
                    {onSecondaryAction && (
                        <Button onClick={onSecondaryAction}>{secondaryActionLabel}</Button>
                    )}
                </ButtonGroup>
            </VerticalStack>
        </div>
    );
}

export default FileUploadCard;
