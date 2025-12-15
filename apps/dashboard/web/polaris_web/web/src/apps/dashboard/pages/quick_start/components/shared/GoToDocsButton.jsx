import { Button } from '@shopify/polaris'

function GoToDocsButton({ docsUrl, variant = 'outline', size, width, ...props }) {
    
    const openDocs = () => {
        if(docsUrl && docsUrl.length > 0){
            window.open(docsUrl, "_blank");
        }
    }

    const buttonContent = (
        <Button 
            {...(variant === 'plain' ? { plain: true } : { outline: true })}
            onClick={openDocs}
            size={size}
            {...props}
        >
            Go to docs
        </Button>
    )

    if (width) {
        return (
            <div style={{width}}>
                {buttonContent}
            </div>
        )
    }

    return buttonContent
}

export default GoToDocsButton