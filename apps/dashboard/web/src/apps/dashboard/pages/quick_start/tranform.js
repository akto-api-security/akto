import {Avatar, Badge} from "@shopify/polaris"
import PostmanSource from "./components/PostmanSource"
import BurpSource from "./components/BurpSource"
import AwsSource from "./components/AwsSource"
import FargateSource from "./components/FargateSource"
import Kubernetes from "./components/Kubernetes"
import FutureConnection from "./components/shared/FutureConnection"
import BannerComponent from "./components/shared/BannerComponent"

const mirroringObj = {
    icon: '/public/aws.svg',
    label: "AWS Mirroring",
    text: "You can deploy Akto in AWS and collect traffic through traffic mirroring.",
    badge: "Recommended",
    docsUrl: 'https://docs.akto.io/traffic-connections/amazon-aws',
    key: "AWS",
    component: <AwsSource />
}

const beanStalkObj = {
    icon: '/public/beanstalk.svg',
    label: "AWS Beanstalk",
    text: "You can deploy Akto in AWS and collect traffic through mirroring on your AWS Beanstalk setup.",
    docsUrl: 'https://docs.akto.io/traffic-connections/aws-beanstalk',
    component: <FutureConnection />,
    key: "Bean Stalk",
}

const eksObj = {
    icon: '/public/eks.svg',
    label: 'AWS EKS',
    text: "You can deploy Akto in AWS and collect traffic through a daemonset on your AWS EKS configuration.",
    docsUrl: 'https://docs.akto.io/traffic-connections/aws-eks',
    component: <Kubernetes docsUrl= 'https://docs.akto.io/traffic-connections/aws-eks' bannerTitle="Setup using AWS EKS"/>,
    key: "EKS"
}

const fargateObj = {
    icon: '/public/fargate.svg',
    label: 'AWS Fargate',
    text: "AWS Fargate allows you to use Amazon ECS to run containers without having to manage servers or clusters of Amazon EC2 instances.", 
    docsUrl: 'https://docs.akto.io/traffic-connections/aws-fargate',
    component: <FargateSource docsUrl='https://docs.akto.io/traffic-connections/aws-fargate' bannerTitle="Setup using Fargate"/>,
    key: "FARGATE"
}

const burpObj = {
    icon: '/public/burp.svg',
    label: "Burp Suite",
    text: "You can deploy Akto on your machine and download Akto's Burp extension to collect API traffic.",   
    docsUrl: 'https://docs.akto.io/traffic-connections/burp-suite',
    key: "BURP",
    component : <BurpSource/>
}

const dockerObj = {
    icon: '/public/docker.svg',
    label: "Docker",
    text: "This setup is recommended only if other setups for AWS or GCP don't work.",
    docsUrl: 'https://docs.akto.io/traffic-connections/docker',
    component: <FargateSource docsUrl="https://docs.akto.io/traffic-connections/docker" bannerTitle="Setup using Docker" />,
    key: "DOCKER"
}

const envoyObj = {
    icon: '/public/envoy.svg',
    label: 'Envoy',
    text: 'Akto-Envoy setup is recommended if your APIs are routed by Envoy.',
    docsUrl: 'https://docs.akto.io/traffic-connections/envoy',
    key: "ENVOY",
    component: <FargateSource docsUrl="https://docs.akto.io/traffic-connections/envoy" bannerTitle="Setup using Envoy" />,
}

const ebpfObj = {
    icon: '/public/ebpf.svg',
    label: 'EBPF',
    text: 'eBPF, the extended Berkeley Packet Filter is a technology that can run sandboxed programs in a privileged context such as the operating system kernel.',
    docsUrl: 'https://docs.akto.io/traffic-connections/ebpf',
    component: <FutureConnection />
}

const gcpObj = {
    icon: '/public/gcp.svg',
    label: 'GCP Mirroring',
    text: 'This setup only takes ten minutes. Once you connect GCP, Akto will process GCP traffic to create an API Inventory in real time.',
    badge: "Recommended",
    docsUrl: 'https://docs.akto.io/traffic-connections/google-cloud-gcp',
    key: "GCP",
    component: <BannerComponent title="Setup using GCP Mirroring" docsUrl="https://docs.akto.io/traffic-connections/google-cloud-gcp"
                    content="Use Google packet mirroring to send duplicate stream of traffic to Akto. No performance impact, only mirrored traffic is used to analyze APIs." />
}

const harFileUploadObj = {
    icon: '/public/HAR.svg',
    label: 'Har File Upload',
    text: "For a very quick view of your inventory, you can upload a HAR file that contains traffic to Akto.",
    docsUrl: 'https://docs.akto.io/traffic-connections/har-file-upload',
    key: "HAR",
    component: <BannerComponent title="Upload .har file" docsUrl="https://docs.akto.io/traffic-connections/har-file-upload"
                    content=" You can use this method if you quickly want to try out Akto. Akto can process HAR (Http ARchive) files and populate inventory from it." />
}

const kongObj = {
    icon: '/public/kong.svg',
    label: 'Kong',
    text: 'Kong Gateway is an open source API gateway, built for multi-cloud and hybrid, and optimized for microservices and distributed architectures.',
    docsUrl: 'https://docs.akto.io/traffic-connections/kong',
    key: "KONG",
    component: <FutureConnection/>
}

const kubernetesObj = {
    icon: '/public/kubernetes.svg',
    label: 'Kubernetes Daemonset',
    text: 'You can deploy Akto in Kubernetes and collect traffic through a daemonset on your Kubernetes configuration.',
    docsUrl: 'https://docs.akto.io/traffic-connections/kubernetes',
    key: "KUBERNETES",
    component: <Kubernetes docsUrl="https://docs.akto.io/traffic-connections/kubernetes" bannerTitle="Setup using Kubernetes Daemonset"/>
}

const nginxObj = {
    icon: '/public/Nginx.svg',
    label: 'NGINX',
    text: 'This setup is recommended if your APIs are routed by NGINX.',
    docsUrl: 'https://docs.akto.io/traffic-connections/nginx',
    key: "NGINX",
    component: <FargateSource docsUrl="https://docs.akto.io/traffic-connections/nginx" bannerTitle="Setup using NGINX" />
}

const postmanObj = {
    icon: '/public/postman.svg',
    label: 'Postman',
    text: 'This setup is recommended if you have updated API collections maintained in Postman.',
    docsUrl: 'https://docs.akto.io/traffic-connections/postman',
    component: <PostmanSource/>,
    key: "POSTMAN"
}

const tcpObj = {
    icon: '/public/TCP.svg',
    label: 'TCP Agent',
    text: ' This setup is recommended only if other setups for AWS or GCP do not work.',
    docsUrl: 'https://docs.akto.io/traffic-connections/tcp-agent',
    key: "NGINX",
    component: <FargateSource docsUrl="https://docs.akto.io/traffic-connections/tcp-agent" bannerTitle="Setup using TCP Agent" />
}

const quick_start_policy_lines= [
    `{`,
    `    "Version": "2012-10-17",`,
    `    "Statement": [`,
    `        {`,
    `            "Sid": "1",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `                "autoscaling:DescribePolicies",`,
    `                "autoscaling:DescribeAutoScalingGroups",`,
    `                "autoscaling:DescribeScalingActivities",`,
    `                "autoscaling:DescribeLaunchConfigurations",`,
    `                "ec2:DescribeSubnets",`,
    `                "ec2:DescribeKeyPairs",`,
    `                "ec2:DescribeSecurityGroups"`,
    `            ],`,
    `            "Resource": "*"`,
    `        },`,
    `        {`,
    `            "Sid": "2",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `                "autoscaling:PutScalingPolicy",`,
    `                "autoscaling:UpdateAutoScalingGroup",`,
    `                "autoscaling:CreateAutoScalingGroup",`,
    `                "autoscaling:StartInstanceRefresh"`,
    `            ],`,
    `            "Resource": [`,
    `                "arn:aws:autoscaling:AWS_REGION:AWS_ACCOUNT_ID:autoScalingGroup:*:autoScalingGroupName/DASHBOARD_STACK_NAME*",`,
    `                "arn:aws:autoscaling:AWS_REGION:AWS_ACCOUNT_ID:autoScalingGroup:*:autoScalingGroupName/MIRRORING_STACK_NAME*"`,
    `            ]`,
    `        },`,
    `        {`,
    `            "Sid": "3",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `                "autoscaling:CreateLaunchConfiguration"`,
    `            ],`,
    `            "Resource": [`,
    `                "arn:aws:autoscaling:AWS_REGION:AWS_ACCOUNT_ID:launchConfiguration:*:launchConfigurationName/MIRRORING_STACK_NAME*",`,
    `                "arn:aws:autoscaling:AWS_REGION:AWS_ACCOUNT_ID:launchConfiguration:*:launchConfigurationName/DASHBOARD_STACK_NAME*"`,
    `             ]`,
    `        },`,
    `        {`,
    `            "Sid": "4",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `                "cloudformation:CreateStack",`,
    `                "cloudformation:DescribeStackResources",`,
    `                "cloudformation:DescribeStacks"`,
    `            ],`,
    `            "Resource": [`,
    `                "arn:aws:cloudformation:AWS_REGION:AWS_ACCOUNT_ID:stack/MIRRORING_STACK_NAME/*",`,
    `                "arn:aws:cloudformation:AWS_REGION:AWS_ACCOUNT_ID:stack/DASHBOARD_STACK_NAME/*"`,
    `            ]`,
    `        },`,
    `        {`,
    `            "Sid": "5",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `               "ec2:CreateTags"`,
    `            ],`,
    `            "Resource": [`,
    `                "*"`,
    `            ]`,
    `        },`,
    `        {`,
    `            "Sid": "6",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `                "ec2:CreateSecurityGroup",`,
    `                "ec2:CreateTags"`,
    `            ],`,
    `            "Resource": [`,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:security-group/*",`,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:security-group-rule/*",`,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:vpc/*"`,
    `            ]`,
    `        },`,
    `        {`,
    `            "Sid": "7", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "ec2:RevokeSecurityGroupEgress", `,
    `                "ec2:AuthorizeSecurityGroupEgress", `,
    `                "ec2:RevokeSecurityGroupIngress", `,
    `                "ec2:AuthorizeSecurityGroupIngress", `,
    `                "ec2:CreateTags"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:security-group/*", `,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:security-group-rule/*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "8", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "ec2:CreateTrafficMirrorTarget"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:traffic-mirror-target/*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "9", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "ec2:ModifyTrafficMirrorFilterNetworkServices", `,
    `                "ec2:CreateTrafficMirrorFilter"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:traffic-mirror-filter/*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "10", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "elasticloadbalancing:DescribeLoadBalancers", `,
    `                "elasticloadbalancing:DescribeListeners", `,
    `                "elasticloadbalancing:DescribeTargetGroups", `,
    `                "ec2:DescribeVpcs"`,
    `            ], `,
    `            "Resource": `,
    `                "*"`,
    `        }, `,
    `        {`,
    `            "Sid": "11", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "elasticloadbalancing:CreateListener", `,
    `                "elasticloadbalancing:ModifyLoadBalancerAttributes", `,
    `                "elasticloadbalancing:AddTags", `,
    `                "elasticloadbalancing:CreateLoadBalancer"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:elasticloadbalancing:AWS_REGION:AWS_ACCOUNT_ID:loadbalancer/net/*akto*/*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "12", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "elasticloadbalancing:AddTags", `,
    `                "elasticloadbalancing:CreateTargetGroup"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:elasticloadbalancing:AWS_REGION:AWS_ACCOUNT_ID:targetgroup/*akto*/*", `,
    `                "arn:aws:elasticloadbalancing:AWS_REGION:AWS_ACCOUNT_ID:targetgroup/*akto*/*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "13", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "lambda:GetFunction", `,
    `                "lambda:CreateFunction", `,
    `                "lambda:DeleteFunction", `,
    `                "lambda:UpdateFunctionConfiguration", `,
    `                "lambda:UpdateFunctionCode", `,
    `                "lambda:GetFunctionCodeSigningConfig", `,
    `                "lambda:InvokeFunction", `,
    `                "lambda:ListFunctions", `,
    `                "lambda:TagResource", `,
    `                "lambda:AddPermission"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:lambda:AWS_REGION:AWS_ACCOUNT_ID:function:*akto*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "14", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "lambda:ListFunctions" `,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:lambda:AWS_REGION:AWS_ACCOUNT_ID:function:*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "15", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "logs:CreateLogStream", `,
    `                "logs:PutRetentionPolicy", `,
    `                "logs:TagResource", `,
    `                "logs:CreateLogGroup"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:logs:AWS_REGION:AWS_ACCOUNT_ID:log-group:/aws/lambda/MIRRORING_STACK_NAME*", `,
    `                "arn:aws:logs:AWS_REGION:AWS_ACCOUNT_ID:log-group:/aws/lambda/MIRRORING_STACK_NAME*:log-stream:"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "16", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "iam:CreateRole", `,
    `                "iam:PutRolePolicy", `,
    `                "iam:GetRole", `,
    `                "iam:GetRolePolicy", `,
    `                "iam:TagRole", `,
    `                "iam:PassRole"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:iam::AWS_ACCOUNT_ID:role/service-role/*akto*", `,
    `                "arn:aws:iam::AWS_ACCOUNT_ID:role/*akto*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "17", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "iam:CreateInstanceProfile", `,
    `                "iam:GetInstanceProfile"`,
    `            ], `,
    `            "Resource": "arn:aws:iam::AWS_ACCOUNT_ID:instance-profile/MIRRORING_STACK_NAME*"`,
    `        }, `,
    `        {`,
    `            "Sid": "18", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "iam:AddRoleToInstanceProfile"`,
    `            ], `,
    `            "Resource": ["arn:aws:iam::AWS_ACCOUNT_ID:instance-profile/MIRRORING_STACK_NAME*", "arn:aws:iam::AWS_ACCOUNT_ID:role/MIRRORING_STACK_NAME-*"]`,
    `        }, `,
    `        {`,
    `            "Sid": "19", `,
    `            "Effect": "Allow", `,
    `            "Action": "s3:GetObject", `,
    `            "Resource": [`,
    `                "arn:aws:s3:::akto-setup-AWS_REGION/templates/get-akto-setup-details.zip", `,
    `                "arn:aws:s3:::akto-setup-AWS_REGION/templates/create-mirror-session.zip", `,
    `                "arn:aws:s3:::akto-setup-AWS_REGION/templates/configure_security_groups.zip", `,
    `                "arn:aws:s3:::akto-setup-AWS_REGION/templates/mirroring-collections-split.zip"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "20", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "events:DescribeRule", `,
    `                "events:PutRule", `,
    `                "events:PutTargets"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:events:AWS_REGION:AWS_ACCOUNT_ID:rule/MIRRORING_STACK_NAME-PeriodicRule"`,
    `            ]`,
    `        } `,
    `    ]`,
    `}`
]

const  quick_start_policy_lines_kubernetes = [
    `{`,
    `    "Version": "2012-10-17",`,
    `    "Statement": [`,
    `        {`,
    `            "Sid": "1",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `                "autoscaling:DescribePolicies",`,
    `                "autoscaling:DescribeAutoScalingGroups",`,
    `                "autoscaling:DescribeScalingActivities",`,
    `                "autoscaling:DescribeLaunchConfigurations",`,
    `                "ec2:DescribeSubnets",`,
    `                "ec2:DescribeKeyPairs",`,
    `                "cloudformation:DescribeStacks",`,
    `                "cloudformation:ListStacks",`,
    `                "ec2:DescribeSecurityGroups"`,
    `            ],`,
    `            "Resource": "*"`,
    `        },`,
    `        {`,
    `            "Sid": "2",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `                "autoscaling:PutScalingPolicy",`,
    `                "autoscaling:UpdateAutoScalingGroup",`,
    `                "autoscaling:CreateAutoScalingGroup",`,
    `                "autoscaling:StartInstanceRefresh"`,
    `            ],`,
    `            "Resource": [`,
    `                "arn:aws:autoscaling:AWS_REGION:AWS_ACCOUNT_ID:autoScalingGroup:*:autoScalingGroupName/*akto*",`,
    `                "arn:aws:autoscaling:AWS_REGION:AWS_ACCOUNT_ID:autoScalingGroup:*:autoScalingGroupName/*akto*"`,
    `            ]`,
    `        },`,
    `        {`,
    `            "Sid": "3",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `                "autoscaling:CreateLaunchConfiguration"`,
    `            ],`,
    `            "Resource": [`,
    `                "arn:aws:autoscaling:AWS_REGION:AWS_ACCOUNT_ID:launchConfiguration:*:launchConfigurationName/*akto*",`,
    `                "arn:aws:autoscaling:AWS_REGION:AWS_ACCOUNT_ID:launchConfiguration:*:launchConfigurationName/*akto*"`,
    `             ]`,
    `        },`,
    `        {`,
    `            "Sid": "4",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `                "cloudformation:CreateStack",`,
    `                "cloudformation:DescribeStackResources"`,
    `            ],`,
    `            "Resource": [`,
    `                "arn:aws:cloudformation:AWS_REGION:AWS_ACCOUNT_ID:stack/*akto*/*",`,
    `                "arn:aws:cloudformation:AWS_REGION:AWS_ACCOUNT_ID:stack/*akto*/*"`,
    `            ]`,
    `        },`,
    `        {`,
    `            "Sid": "5",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `               "ec2:CreateTags"`,
    `            ],`,
    `            "Resource": [`,
    `                "*"`,
    `            ]`,
    `        },`,
    `        {`,
    `            "Sid": "6",`,
    `            "Effect": "Allow",`,
    `            "Action": [`,
    `                "ec2:CreateSecurityGroup",`,
    `                "ec2:CreateTags"`,
    `            ],`,
    `            "Resource": [`,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:security-group/*",`,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:security-group-rule/*",`,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:vpc/*"`,
    `            ]`,
    `        },`,
    `        {`,
    `            "Sid": "7", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "ec2:RevokeSecurityGroupEgress", `,
    `                "ec2:AuthorizeSecurityGroupEgress", `,
    `                "ec2:RevokeSecurityGroupIngress", `,
    `                "ec2:AuthorizeSecurityGroupIngress", `,
    `                "ec2:CreateTags"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:security-group/*", `,
    `                "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:security-group-rule/*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "10", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "elasticloadbalancing:DescribeLoadBalancers", `,
    `                "elasticloadbalancing:DescribeListeners", `,
    `                "elasticloadbalancing:DescribeTargetGroups", `,
    `                "ec2:DescribeVpcs"`,
    `            ], `,
    `            "Resource": `,
    `                "*"`,
    `        }, `,
    `        {`,
    `            "Sid": "11", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "elasticloadbalancing:CreateListener", `,
    `                "elasticloadbalancing:ModifyLoadBalancerAttributes", `,
    `                "elasticloadbalancing:AddTags", `,
    `                "elasticloadbalancing:CreateLoadBalancer"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:elasticloadbalancing:AWS_REGION:AWS_ACCOUNT_ID:loadbalancer/net/*akto*/*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "12", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "elasticloadbalancing:AddTags", `,
    `                "elasticloadbalancing:CreateTargetGroup"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:elasticloadbalancing:AWS_REGION:AWS_ACCOUNT_ID:targetgroup/*akto*/*", `,
    `                "arn:aws:elasticloadbalancing:AWS_REGION:AWS_ACCOUNT_ID:targetgroup/*akto*/*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "13", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "lambda:GetFunction", `,
    `                "lambda:CreateFunction", `,
    `                "lambda:DeleteFunction", `,
    `                "lambda:UpdateFunctionConfiguration", `,
    `                "lambda:UpdateFunctionCode", `,
    `                "lambda:GetFunctionCodeSigningConfig", `,
    `                "lambda:InvokeFunction", `,
    `                "lambda:ListFunctions", `,
    `                "lambda:TagResource", `,
    `                "lambda:AddPermission"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:lambda:AWS_REGION:AWS_ACCOUNT_ID:function:*akto*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "14", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "lambda:ListFunctions" `,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:lambda:AWS_REGION:AWS_ACCOUNT_ID:function:*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "15", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "logs:CreateLogStream", `,
    `                "logs:PutRetentionPolicy", `,
    `                "logs:TagResource", `,
    `                "logs:CreateLogGroup"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:logs:AWS_REGION:AWS_ACCOUNT_ID:log-group:/aws/lambda/*akto*", `,
    `                "arn:aws:logs:AWS_REGION:AWS_ACCOUNT_ID:log-group:/aws/lambda/*akto*:log-stream:"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "16", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "iam:CreateRole", `,
    `                "iam:PutRolePolicy", `,
    `                "iam:GetRole", `,
    `                "iam:GetRolePolicy", `,
    `                "iam:TagRole", `,
    `                "iam:PassRole"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:iam::AWS_ACCOUNT_ID:role/service-role/*akto*", `,
    `                "arn:aws:iam::AWS_ACCOUNT_ID:role/*akto*"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "17", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "iam:CreateInstanceProfile", `,
    `                "iam:GetInstanceProfile"`,
    `            ], `,
    `            "Resource": "arn:aws:iam::AWS_ACCOUNT_ID:instance-profile/*akto*"`,
    `        }, `,
    `        {`,
    `            "Sid": "18", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "iam:AddRoleToInstanceProfile"`,
    `            ], `,
    `            "Resource": ["arn:aws:iam::AWS_ACCOUNT_ID:instance-profile/*akto*", "arn:aws:iam::AWS_ACCOUNT_ID:role/*akto*"]`,
    `        }, `,
    `        {`,
    `            "Sid": "19", `,
    `            "Effect": "Allow", `,
    `            "Action": "s3:GetObject", `,
    `            "Resource": [`,
    `                "arn:aws:s3:::akto-setup-AWS_REGION/templates/get-akto-setup-details.zip", `,
    `                "arn:aws:s3:::akto-setup-AWS_REGION/templates/create-mirror-session.zip", `,
    `                "arn:aws:s3:::akto-setup-AWS_REGION/templates/configure_security_groups.zip", `,
    `                "arn:aws:s3:::akto-setup-AWS_REGION/templates/mirroring-collections-split.zip"`,
    `            ]`,
    `        }, `,
    `        {`,
    `            "Sid": "20", `,
    `            "Effect": "Allow", `,
    `            "Action": [`,
    `                "events:DescribeRule", `,
    `                "events:PutRule", `,
    `                "events:PutTargets"`,
    `            ], `,
    `            "Resource": [`,
    `                "arn:aws:events:AWS_REGION:AWS_ACCOUNT_ID:rule/*akto*"`,
    `            ]`,
    `        } `,
    `    ]`,
    `}`
]

const yaml_fargate =[
    `"AKTO_NLB": "<AKTO_NLB_IP>",`,
    `"AKTO_MONGO_IP": "<AKTO_MONGO_CONN>"`   
]

const yaml_kubernetes = [
    `apiVersion: apps/v1`,
    `kind: DaemonSet`,
    `metadata:`,
    `  name: akto-k8s`,
    `  namespace: {NAMESPACE}`,
    `  labels:`,
    `    app: {APP_NAME}`,
    `spec:`,
    `  selector:`,
    `    matchLabels:`,
    `      app: {APP_NAME}`,
    `  template:`,
    `    metadata:`,
    `      labels:`,
    `        app: {APP_NAME}`,
    `    spec:`,
    `      hostNetwork: true`,
    `      containers:`,
    `      - name: mirror-api-logging`,
    `        image: aktosecurity/mirror-api-logging:k8s_agent`,
    `        env: `,
    `          - name: AKTO_TRAFFIC_BATCH_TIME_SECS`,
    `            value: "10"`,
    `          - name: AKTO_TRAFFIC_BATCH_SIZE`,
    `            value: "100"`,
    `          - name: AKTO_INFRA_MIRRORING_MODE`,
    `            value: "gcp"`,
    `          - name: AKTO_KAFKA_BROKER_MAL`,
    `            value: "<AKTO_NLB_IP>:9092"`,
    `          - name: AKTO_MONGO_CONN`,
    `            value: "<AKTO_MONGO_CONN>"`,
]

const quickStartFunc = {
    getConnectorsList: function (){
        const connectorsList = [mirroringObj, gcpObj, kubernetesObj, fargateObj, nginxObj, burpObj, postmanObj,
           beanStalkObj, eksObj, dockerObj, envoyObj, ebpfObj,
           harFileUploadObj, kongObj, tcpObj
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
    },

    getPolicyLines: function(key){
        switch(key) {
            case "AWS":
                return quick_start_policy_lines

            case "FARGATE":
                return quick_start_policy_lines_kubernetes

            case "KUBERNETES":
                return quick_start_policy_lines_kubernetes

            default:
                return []
        }
    },

    getYamlLines: function(key){
        switch(key){
            case "FARGATE":
                return yaml_fargate

            case "KUBERNETES":
                return yaml_kubernetes

            default :
                return []
        }
    },

    convertLbList: function(lbList){
        const arr = lbList.map((item) => {
            return{
                label: item.resourceName,
                value: item.resourceId,
            }
        })
        return arr
    },

    getValuesArr: function(lbList){
        let arr = []
        lbList.forEach((element) => {
            arr.push(element.resourceId)
        })
        return arr
    },

    getLBListFromValues: function(valuesArr,mapNameToId){
        const arr = valuesArr.map((element) => {
            return{
                resourceId: element,
                resourceName: mapNameToId[element]
            }
        })
        return arr
    },

    getDesiredSteps: function(url) {
        const steps = [
            {
              text: "Grab the policy JSON below and navigate to Akto Dashboard's current role by clicking ",
              textComponent: <a target='_blank' href={url}>here</a>, 
            },
            {
              text: "We will create an inline policy, navigate to JSON tab and paste the copied JSON here."
            },
            {
              text: "Click on 'Review policy'."
            },
            {
              text: "Now lets name the policy as 'AktoDashboardPolicy'."
            },
            {
              text: "Finally create the policy by clicking on 'Create policy'."
            },
        ]
        return steps
    },

    renderProgressBar: function (creationTimeInMs, progressBar){
        const progressBarCopy = JSON.parse(JSON.stringify(progressBar))
        progressBarCopy.show = true;
        const currTimeInMs = Date.now();
        const maxDeploymentTimeInMs = progressBarCopy.max_deployment_time_in_ms;
        let progressPercent = ((currTimeInMs - creationTimeInMs) * 100) / maxDeploymentTimeInMs
        if (progressPercent > 90) {
            progressPercent = 90;
        }
        // to add more else if blocks to handle cases where deployment is stuck
        progressBarCopy.value = Math.round(progressPercent);
        return progressBarCopy
      },
  
      removeProgressBarAndStatuschecks: function(progressBar) {
        const progressBarCopy = JSON.parse(JSON.stringify(progressBar))
        progressBarCopy.show = false;
        progressBarCopy.value = 0;
        return progressBarCopy
      }
}

export default quickStartFunc