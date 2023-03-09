<template>
	<!-- This template should be rewritten with slots and props -->
	<div>
		<div class="bg">
			<div class="d-flex">
				<div class="mr-3">
					<v-icon class="info-icon">$fas_info-circle</v-icon>
				</div>
				<div>
					<div class="title">
					Invite new members
					</div>
					<div class="content mt-1">
					Inviting team members is disabled in local. Collaborate with your team by using Akto cloud or AWS/GCP deploy.
					</div>
					<div class="cta mt-3">
					<a href="https://docs.akto.io/getting-started/quick-start-with-akto-cloud" target="_blank" class="clickable-link">
						Go to docs 
						<v-icon class="icon-nav-drawer">$fas_arrow-right</v-icon>
					</a>
					</div>
				</div>
			</div>
		</div>
	</div>
</template>

<script>
export default{
	name: 'BannerVertical'
}
</script>

<style scoped>

.bg{
	background: var(--hexColor27);
	border: 1px solid var(--lighten2);
	border-radius: 12px;
	padding: 16px;
}
.title{
	font-family: 'Poppins';
	font-style: normal;
	font-weight: 600;
	font-size: 14px !important;
	line-height: 20px;
	color: var(--themeColor);
}

.content{
	font-family: 'Poppins';
	font-style: normal;
	font-weight: 400;
	font-size: 14px !important;
	line-height: 20px;
	color: var(--themeColor);
}

.cta{
	font-family: 'Poppins';
	font-style: normal;
	font-weight: 600;
	font-size: 14px !important;
	line-height: 20px;
	color: var(--themeColor);
}
.icon-nav-drawer{
  justify-content: center;
  margin-top: 2px !important;
  margin-bottom: 2px !important;
  color: var(--themeColor);
}

.info-icon{
  color: var(--themeColor);
}

.clickable-link{
    cursor: pointer;
}
</style>