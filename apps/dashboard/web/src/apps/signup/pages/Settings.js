import { Badge, Box, Button, Card, Frame, Grid, HorizontalGrid, HorizontalStack, Icon, LegacyCard, Modal, Navigation, Page, Text, TextContainer } from "@shopify/polaris"
import { Fragment, useCallback, useState } from "react";
import { SettingsMinor, CancelMajor } from "@shopify/polaris-icons"
import { tokens } from '@shopify/polaris-tokens';
import Header from "../../dashboard/components/layouts/Headers";
import { Outlet, useNavigate } from "react-router-dom";

// const Settings = () => {
//     const navigate = useNavigate();

//     const settingsCntainerStyles = {
//         backgroundColor: "rgba(0,0,0,0.4)",
//         zIndex: 1000,
//         width: "100%",
//         height: "100%",
//         position: "fixed",
//         margin: 0,
//         top: 0,
//         left: 0,
//         overflow: "hidden"
//     }

//     const settingsOverlayStyles = {
//         backgroundColor: tokens.color['color-bg'],
//         marginTop: "5vh",
//         height: "100%"
//     }

//     return (
//         <div style={settingsCntainerStyles}>
//             <div style={settingsOverlayStyles}>
//                 <div style={{ height: "5vh" }}>
//                     <Grid>
//                         <Grid.Cell columnSpan={{ xl: 1 }}>
//                             <Icon
//                                 p
//                                 source={SettingsMinor}
//                             />
//                         </Grid.Cell>
//                         <Grid.Cell columnSpan={{ xl: 10 }}>
//                             <Text variant="headingLg">
//                                 Settings
//                             </Text>
//                         </Grid.Cell>
//                         <Grid.Cell columnSpan={{ xl: 1 }}>
//                             <div onClick={() => navigate("/")}>
//                             <Icon
//                                 source={CancelMajor}
//                             />
//                             </div>
//                         </Grid.Cell>
//                     </Grid>
//                 </div>
//                 <div style={{ backgroundColor: tokens.color['color-bg-subdued'], height: "100%", padding: "5vh 8vw", display: "grid", gridTemplateColumns: "1fr 3fr", gap: "1vh" }}>
//                     <div>
//                     <Card background="subdued" roundedAbove="sm">
//                         Account Management
//                     </Card>
//                     <Navigation location="/">
//                         <Navigation.Section
//                         items={[
//                             {
//                             url: '#',
//                             label: 'Home',
//                             icon: CancelMajor,
//                             },
//                             {
//                             url: '#',
//                             excludePaths: ['#'],
//                             label: 'Orders',
//                             icon: CancelMajor,
//                             badge: '15',
//                             },
//                             {
//                             url: '#',
//                             excludePaths: ['#'],
//                             label: 'Products',
//                             icon: CancelMajor,
//                             },
//                         ]}
//                         />
//                     </Navigation>
//                     </div>
//                     <Page
//                         backAction={{ content: 'Products', url: '#' }}
//                         title="3/4 inch Leather pet collar"
//                         titleMetadata={<Badge status="success">Paid</Badge>}
//                         subtitle="Perfect for any pet"
//                         compactTitle
//                         primaryAction={{ content: 'Save', disabled: true }}
//                         secondaryActions={[
//                             {
//                                 content: 'Duplicate',
//                                 accessibilityLabel: 'Secondary action label',
//                                 onAction: () => alert('Duplicate action'),
//                             },
//                             {
//                                 content: 'View on your store',
//                                 onAction: () => alert('View on your store action'),
//                             },
//                         ]}
//                         actionGroups={[
//                             {
//                                 title: 'Promote',
//                                 actions: [
//                                     {
//                                         content: 'Share on Facebook',
//                                         accessibilityLabel: 'Individual action label',
//                                         onAction: () => alert('Share on Facebook action'),
//                                     },
//                                 ],
//                             },
//                         ]}
//                         pagination={{
//                             hasPrevious: true,
//                             hasNext: true,
//                         }}
//                     >
//                         <LegacyCard title="Credit card" sectioned>
//                             <p>Credit card information</p>
//                         </LegacyCard>
//                     </Page>
//                 </div>
//             </div>
//         </div>
//     )
// }


// const Settings = () => {
//     const [active, setActive] = useState(true);

//     const handleChange = useCallback(() => setActive(!active), [active]);

//     const activator = <Button onClick={handleChange}>Open</Button>;

//     return (
//         <div className="settings-overlay">
//         <Modal
//           large
//           activator={activator}
//           open={active}
//           onClose={handleChange}
//           title="Reach more shoppers with Instagram product tags"
//           primaryAction={{
//             content: 'Add Instagram',
//             onAction: handleChange,
//           }}
//           secondaryActions={[
//             {
//               content: 'Learn more',
//               onAction: handleChange,
//             },
//           ]}
//         >
//           <Modal.Section>
//             <TextContainer>
//               <p>
//                 Use Instagram posts to share your products with millions of
//                 people. Let shoppers buy from your store without leaving
//                 Instagram.
//               </p>
//             </TextContainer>
//           </Modal.Section>
//         </Modal>
//         </div>
//     );
// }

const Settings = () => {

    return (
        <div>
            <Outlet/>
        </div>
    )
}

export default Settings