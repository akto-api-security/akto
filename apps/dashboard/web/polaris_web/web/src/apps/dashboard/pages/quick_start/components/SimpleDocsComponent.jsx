import { VerticalStack, Button, Text, Divider } from '@shopify/polaris'

function SimpleDocsComponent({description, docsUrl}) {

  return (
      <div className='card-items'>
            <VerticalStack gap='4' align="start">
                <Text variant='bodyMd'>
                    {description}
                </Text>
                <Divider />
                <div style={{width: '50%'}}>
                    <Button outline onClick={
                        () => {
                        window.open(docsUrl, "_blank");
                    }
                    }>
                    Go to docs
                    </Button>
                </div>
            </VerticalStack>
        </div>
  )
}

export default SimpleDocsComponent