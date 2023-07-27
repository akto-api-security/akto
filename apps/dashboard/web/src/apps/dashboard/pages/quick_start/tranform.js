import {Avatar, Badge} from "@shopify/polaris"
import PostmanSource from "./components/PostmanSource"
import BurpSource from "./components/BurpSource"

const mirroringObj = {
    icon: '/public/aws.svg',
    label: "AWS Mirroring",
    text: "You can deploy Akto in AWS and collect traffic through traffic mirroring.",
    badge: "Recommended",
    docsUrl: 'https://docs.akto.io/traffic-connections/amazon-aws',
    key: "AWS"
}

const beanStalkObj = {
    icon: '/public/beanstalk.svg',
    label: "AWS Beanstalk",
    text: "You can deploy Akto in AWS and collect traffic through mirroring on your AWS Beanstalk setup.",
    docsUrl: 'https://docs.akto.io/traffic-connections/aws-beanstalk'
}

const eksObj = {
    icon: '/public/eks.svg',
    label: 'AWS EKS',
    text: "You can deploy Akto in AWS and collect traffic through a daemonset on your AWS EKS configuration.",
    docsUrl: 'https://docs.akto.io/traffic-connections/aws-eks'
}

const fargateObj = {
    icon: '/public/fargate.svg',
    label: 'AWS Fargate',
    text: "AWS Fargate allows you to use Amazon ECS to run containers without having to manage servers or clusters of Amazon EC2 instances.", 
    docsUrl: 'https://docs.akto.io/traffic-connections/aws-fargate'
}

const burpObj = {
    icon: '/public/burp.svg',
    label: "Burp Suite",
    text: "You can deploy Akto on your machine and download Akto's Burp extension to collect API traffic.",   
    badge: "Recommended",
    docsUrl: 'https://docs.akto.io/traffic-connections/burp-suite',
    key: "BURP",
    component : <BurpSource/>
}

const dockerObj = {
    icon: '/public/docker.svg',
    label: "Docker",
    text: "This setup is recommended only if other setups for AWS or GCP don't work.",
    docsUrl: 'https://docs.akto.io/traffic-connections/docker'
}

const envoyObj = {
    icon: '/public/envoy.svg',
    label: 'Envoy',
    text: 'Akto-Envoy setup is recommended if your APIs are routed by Envoy.',
    docsUrl: 'https://docs.akto.io/traffic-connections/envoy'
}

const ebpfObj = {
    icon: '/public/ebpf.svg',
    label: 'EBPF',
    text: 'eBPF, the extended Berkeley Packet Filter is a technology that can run sandboxed programs in a privileged context such as the operating system kernel.',
    docsUrl: 'https://docs.akto.io/traffic-connections/ebpf'
}

const gcpObj = {
    icon: '/public/gcp.svg',
    label: 'GCP Mirroring',
    text: 'This setup only takes ten minutes. Once you connect GCP, Akto will process GCP traffic to create an API Inventory in real time.',
    badge: "Recommended",
    docsUrl: 'https://docs.akto.io/traffic-connections/google-cloud-gcp',
    key: "GCP",
}

const harFileUploadObj = {
    icon: '/public/HAR.svg',
    label: 'Har File Upload',
    text: "For a very quick view of your inventory, you can upload a HAR file that contains traffic to Akto.",
    docsUrl: 'https://docs.akto.io/traffic-connections/har-file-upload'
}

const kongObj = {
    icon: '/public/kong.svg',
    label: 'Kong',
    text: 'Kong Gateway is an open source API gateway, built for multi-cloud and hybrid, and optimized for microservices and distributed architectures.',
    docsUrl: 'https://docs.akto.io/traffic-connections/kong'
}

const kubernetesObj = {
    icon: '/public/kubernetes.svg',
    label: 'Kubernetes Daemonset',
    text: 'You can deploy Akto in Kubernetes and collect traffic through a daemonset on your Kubernetes configuration.',
    docsUrl: 'https://docs.akto.io/traffic-connections/kubernetes',
}

const nginxObj = {
    icon: '/public/Nginx.svg',
    label: 'NGINX',
    text: 'This setup is recommended if your APIs are routed by NGINX.',
    docsUrl: 'https://docs.akto.io/traffic-connections/nginx',
    key: "NGINX",
}

const postmanObj = {
    icon: '/public/postman.svg',
    label: 'Postman',
    text: 'This setup is recommended if you have updated API collections maintained in Postman.',
    badge: 'Recommened',
    docsUrl: 'https://docs.akto.io/traffic-connections/postman',
    component: <PostmanSource/>,
    key: "POSTMAN"
}

const tcpObj = {
    icon: '/public/TCP.svg',
    label: 'TCP Agent',
    text: ' This setup is recommended only if other setups for AWS or GCP do not work.',
    docsUrl: 'https://docs.akto.io/traffic-connections/tcp-agent'
}

const quickStartFunc = {
    getConnectorsList: function (){
        const connectorsList = [mirroringObj, gcpObj, burpObj, postmanObj,
           beanStalkObj, eksObj, fargateObj, dockerObj, envoyObj, ebpfObj,
           harFileUploadObj, kongObj, kubernetesObj, nginxObj, tcpObj
        ]
        return connectorsList
    },

    convertListForMenu: function(items) {
        const arr = items.map((item,index)=> {
            let label = (
                <div style={{display: 'flex', gap: '4px', alignItems: 'center'}}>
                    <Avatar customer size="extraSmall" name={item.label} source={item.icon}/>
                    {item.label}
                    {item.badge  ? <Badge size='small' status='success'>{item.badge}</Badge> : null}
                </div>
            )
            return{
                id: index + 1,
                label: label,
                value: item.label,
            }
        })

        return arr
    },

    getConnectionsObject: function(configuredItems, allItems){
        let moreConnections = []
        let myConnections = []

        allItems.forEach(element => {
            if(element.key && configuredItems.includes(element.key)){
                myConnections.push(element)
            }else{
                moreConnections.push(element)
            }
        });

        return {moreConnections,myConnections}
    }
}

export default quickStartFunc