import { VerticalStack, Text, Divider } from '@shopify/polaris'
import GoToDocsButton from './shared/GoToDocsButton'

function SimpleDocsComponent({description, docsUrl}) {

  return (
      <div className='card-items'>
            <VerticalStack gap='4' align="start">
                <Text variant='bodyMd'>
                    {description}
                </Text>
                <Divider />
                <GoToDocsButton docsUrl={docsUrl} width='50%' />
            </VerticalStack>
        </div>
  )
}

export default SimpleDocsComponent