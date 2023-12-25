<template>

    <spinner v-if="loading" />
    <div v-else class="lb_dropdown">
        <div v-if="isLocalDeploy">
            <div>
                Use AWS packet mirroring to send duplicate stream of traffic to Akto. 
                No performance impact, only mirrored traffic is used to analyze APIs. 
                <div v-if="!isLocalDeploy"><a  class="clickable-docs" _target="blank" href="https://docs.akto.io/getting-started/quick-start-with-akto-self-hosted/aws-deploy">Know more</a></div>
            </div>
          <banner-horizontal class="mt-3">
            <div slot="content">
              <div>To setup traffic mirroring from AWS, deploy in AWS. Go to <a class="clickable-docs" target="blank" href="https://docs.akto.io/getting-started/quick-start-with-akto-self-hosted/aws-deploy">docs</a>.</div>
            </div>
          </banner-horizontal>
        </div>
        <div v-else>
            <div v-if="hasRequiredAccess">
                    <v-select v-model="selectedLBs" append-icon="$fas_edit" :items="availableLBs" item-text="resourceName" item-value="resourceId"
                        label="Select Loadbalancer(s)" return-object multiple>
                        <template v-slot:selection="{ item, index }">
                            <v-chip v-if="index === 0">
                                <span>{{ item.resourceName }}</span>
                            </v-chip>
                            <span v-if="index === 1" class="grey--text text-caption">
                                (+{{ selectedLBs.length - 1 }} others)
                            </span>
                        </template>
                        <template v-slot:item="{ active, item, attrs, on }">
                            <v-list-item :class="item.alreadySelected ? 'disabled_lb' : ''" v-on="on" v-bind="attrs" #default="{ active }">
                                <v-list-item-action>
                                    <v-checkbox :input-value="active" on-icon="$far_check-square" off-icon="$far_square">
                                    </v-checkbox>
                                </v-list-item-action>
                                <v-list-item-content>
                                    <v-list-item-title>
                                        <v-row no-gutters align="center">
                                            <span>{{ item.resourceName }}</span>
                                        </v-row>
                                    </v-list-item-title>
                                </v-list-item-content>
                            </v-list-item>
                        </template>

                        <template slot="append-outer" >
                            <spinner v-if="progressBar.show"></spinner>
                            <v-btn v-else primary dark color="var(--themeColor)" @click="saveLBs" :disabled="selectedLBs.length === initialLBCount" class="ml-3">
                                Apply
                            </v-btn>
                        </template>
                    </v-select>
                
                <div class="text_msg" v-html="text_msg"></div>
                <div v-if="progressBar.show">
                    <div class="d-flex">
                        <v-progress-linear class="mt-2" background-color="var(--rgbaColor13)" color="var(--rgbaColor7)"
                            :value="progressBar.value">
                        </v-progress-linear>
                        <div class="ml-2">{{ progressBar.value }}%</div>
                    </div>
                </div>
            </div>
            <div v-else>
                <div class="steps">Your dashboard's instance needs relevant access to setup traffic mirroring, please
                    do the
                    following steps:</div>
                <div class="steps">
                    <b>Step 1</b>: Grab the policy JSON below and navigate to Akto Dashboard's current role by clicking <a target="_blank" class="clickable-docs" :href="getAktoDashboardRoleUpdateUrl()">here</a>
                    <code-block :lines="quick_start_policy_lines" onCopyBtnClickText="Policy copied to clipboard"></code-block>
                </div>
                <div class="steps">
                    <b>Step 2</b>: We will create an inline policy, navigate to JSON tab and paste the copied JSON here.
                </div>
                <div class="steps">
                    <b>Step 3</b>: Click on 'Review policy'.
                </div>
                <div class="steps">
                    <b>Step 4</b>: Now lets name the policy as 'AktoDashboardPolicy'.
                </div>
                <div class="steps">
                    <b>Step 5</b>: Finally create the policy by clicking on 'Create policy'.
                </div>
                <div class="steps">
                    <b>Step 6</b>: Click <a class="clickable-docs" href="/dashboard/quick-start">here</a> to refresh.
                </div>
            </div>
        </div>
    </div>

</template>


<script>
import api from '../api.js'
import CodeBlock from '@/apps/dashboard/shared/components/CodeBlock'
import Spinner from '@/apps/dashboard/shared/components/Spinner'
import BannerHorizontal from '../../../shared/components/BannerHorizontal.vue'
export default {
    name: 'LoadBalancers',
    components: {
        CodeBlock,
        Spinner,
        BannerHorizontal
    },
    data() {
        return {
            initialCall: true,
            loading: true,
            hasRequiredAccess: false,
            availableLBs: [],
            selectedLBs: [],
            existingSelectedLBs: [],
            stackCreationStartTime: null,
            progressBar: {
                show: false,
                value: 0,
                max_deployment_time_in_ms: 8 * 60 * 1000,
            },
            initialLBCount: 0,
            text_msg: null,
            quick_start_policy_lines: [
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
            ],
            aktoDashboardRoleName: null,
            isLocalDeploy: false,
            deploymentMethod: "AWS_TRAFFIC_MIRRORING"
        }
    },
    mounted() {
        this.fetchLBs();
    },
    methods: {
        getAktoDashboardRoleUpdateUrl(){
            return "https://us-east-1.console.aws.amazon.com/iam/home#/roles/" + this.aktoDashboardRoleName  + "$createPolicy?step=edit";
        },
        fetchLBs() {
            if((window.DASHBOARD_MODE && window.DASHBOARD_MODE.toLowerCase() === 'local_deploy') || (window.CLOUD_TYPE && window.CLOUD_TYPE.toLowerCase() === 'gcp')){
                this.loading = false;
                this.isLocalDeploy = true;
            } else {
                api.fetchLBs({deploymentMethod: this.deploymentMethod}).then((resp) => {
                    if (!resp.dashboardHasNecessaryRole) {
                        for (let i = 0; i < this.quick_start_policy_lines.length; i++) {
                            let line = this.quick_start_policy_lines[i];
                            line = line.replaceAll('AWS_REGION', resp.awsRegion);
                            line = line.replaceAll('AWS_ACCOUNT_ID', resp.awsAccountId);
                            line = line.replaceAll('MIRRORING_STACK_NAME', resp.aktoMirroringStackName);
                            line = line.replaceAll('DASHBOARD_STACK_NAME', resp.aktoDashboardStackName);
                            this.quick_start_policy_lines[i] = line;
                        }
                    }
                    this.hasRequiredAccess = resp.dashboardHasNecessaryRole
                    this.selectedLBs = resp.selectedLBs;
                    for(let i=0; i<resp.availableLBs.length; i++){
                        let lb = resp.availableLBs[i];
                        let alreadySelected = false;
                        for(let j=0; j<this.selectedLBs.length; j++){
                            if(this.selectedLBs[j].resourceName === lb.resourceName){
                                alreadySelected = true;
                            }
                        }
                        lb['alreadySelected'] = alreadySelected;
                    }
                    this.availableLBs = resp.availableLBs;
                    this.existingSelectedLBs = resp.selectedLBs;
                    this.initialLBCount = this.selectedLBs.length;
                    this.aktoDashboardRoleName = resp.aktoDashboardRoleName;
                    this.checkStackState()
                })
            }
        },
        saveLBs() {
            api.saveLBs(this.selectedLBs).then((resp) => {
                this.availableLBs = resp.availableLBs;
                this.selectedLBs = resp.selectedLBs;
                this.existingSelectedLBs = resp.selectedLBs;
                if (resp.isFirstSetup) {
                    this.checkStackState()
                    mixpanel.track("mirroring_stack_creation_initialized");
                } else {
                    mixpanel.track("loadbalancers_updated");
                }
            })
        },
        checkStackState() {
            let intervalId = null;
            intervalId = setInterval(async () => {
                api.fetchStackCreationStatus({deploymentMethod: this.deploymentMethod}).then((resp) => {
                    if (this.initialCall) {
                        this.initialCall = false;
                        this.loading = false;
                    }
                    this.handleStackState(resp.stackState, intervalId)
                }
                )
            }, 5000)
        },
        handleStackState(stackState, intervalId) {
            if (stackState.status == 'CREATE_IN_PROGRESS') {
                this.renderProgressBar(stackState.creationTime)
                this.text_msg = 'We are setting up mirroring for you! Grab a cup of coffee, sit back and relax while we work our magic!';
            }
            else if (stackState.status == 'CREATE_COMPLETE') {
                this.removeProgressBarAndStatuschecks(intervalId);
                this.text_msg = 'Akto is tirelessly processing mirrored traffic to protect your APIs. Click <a class="clickable-docs" href="/dashboard/observe/inventory">here</a> to navigate to API Inventory.';
            }
            else if (stackState.status == 'DOES_NOT_EXISTS') {
                this.removeProgressBarAndStatuschecks(intervalId);
                this.text_msg = 'Mirroring is not setup currently, choose 1 or more LBs to enable mirroring.';
            } else if (stackState.status == 'TEMP_DISABLE') {
                this.removeProgressBarAndStatuschecks(intervalId);
                this.text_msg = 'Current deployment is in progress, please refresh this page in sometime.';
            } else {
                this.removeProgressBarAndStatuschecks(intervalId);
                this.text_msg = 'Something went wrong while setting up mirroring, please write to us at support@akto.io'
            }
        },
        renderProgressBar(creationTimeInMs) {
            this.progressBar.show = true;
            const currTimeInMs = Date.now();
            const maxDeploymentTimeInMs = this.progressBar.max_deployment_time_in_ms;
            let progressPercent = (currTimeInMs - creationTimeInMs) / maxDeploymentTimeInMs * 100;
            if (progressPercent > 90) {
                progressPercent = 90;
            }
            // to add more else if blocks to handle cases where deployment is stuck
            this.progressBar.value = Math.round(progressPercent);
        },
        removeProgressBarAndStatuschecks(intervalId) {
            this.progressBar.show = false;
            this.progressBar.value = 0;
            clearInterval(intervalId);
        }
    }
}
</script>


<style scoped>
.disabled_lb{
    pointer-events: none;
    opacity: 0.5;
}

.steps{
    margin-top: 6px;
}

.clickable-docs{
    cursor: pointer;
    color: var(--quickStartTheme) !important;
    text-decoration: underline;
}

.text_msg >>> .clickable-docs{
    cursor: pointer;
    color: var(--quickStartTheme) !important;
    text-decoration: underline;
} 
</style>
