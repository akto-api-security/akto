const mirroringObj = {
    icon: '',
    label: "AWS Mirroring",
    text: "Traffic Mirroring is an Amazon VPC feature that you can use to copy network traffic from an elastic network interface of type interface.",
    badge: "Recommended"
}

const beanStalkObj = {
    icon: '',
    label: "AWS Beanstalk",
    text: "AWS Elastic Beanstalk automatically handles the details of capacity provisioning, load balancing, scaling, and application health monitoring."
}

const eksObj = {
    icon: '',
    label: 'AWS EKS',
    text: "Amazon EKS automatically manages the availability and scalability of the Kubernetes control plane nodes responsible for scheduling containers, managing application availability, storing cluster data, and other key tasks."
}

const fargateObj = {
    icon: '',
    label: 'AWS Fargate',
    text: "AWS Fargate allows you to use Amazon ECS to run containers without having to manage servers or clusters of Amazon EC2 instances.", 
}

const burpObj = {
    icon: '',
    label: "Burp Suite",
    text: "Burp Proxy operates as a web proxy server between the browser and target applications. It enables you to intercept, inspect, and modify traffic that passes in both directions.",
    badge: "Recommended"
}

const dockerObj = {
    icon: '',
    label: "Docker",
    text: "Docker is a platform designed to help developers build, share, and run modern applications."
}

const envoyObj = {
    icon: '',
    label: 'Envoy',
    text: 'Envoy is an open-source edge and service proxy designed for cloud-native applications. Akto-Envoy setup is recommended if your APIs are routed by Envoy.'
}

const ebpfObj = {
    icon: '',
    label: 'Envoy',
    text: 'eBPF, the extended Berkeley Packet Filter is a technology that can run sandboxed programs in a privileged context such as the operating system kernel.'
}

const gcpObj = {
    icon: '',
    label: 'GCP Mirroring',
    text: 'GCP Packet Mirroring clones the traffic of specified instances in your Virtual Private Cloud (VPC) network and forwards it for examination.',
    badge: "Recommended"
}

const harFileUploadObj = {
    icon: '',
    label: 'Har File Upload',
    text: "The HTTP Archive format, or HAR, is a JSON-formatted archive file format for logging of a web browser's interaction with a site. The common extension for these files is .har."
}

const kongObj = {
    icon: '',
    label: 'Kong',
    text: 'Kong Gateway is an open source API gateway, built for multi-cloud and hybrid, and optimized for microservices and distributed architectures.'
}

const quickStartFunc = {
    getConnectorsList: function (){
        const connectorsList = [mirroringObj, gcpObj, burpObj,
            beanStalkObj, eksObj, fargateObj, dockerObj, envoyObj, ebpfObj, harFileUploadObj,
            kongObj, 
        ]
        return connectorsList
    }
}

export default quickStartFunc