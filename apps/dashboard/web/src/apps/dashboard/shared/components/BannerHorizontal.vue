<template>
	<div class="bg">
		<div class="d-flex">
			<div class="mr-3 ml-1"> <v-icon class="icon-nav-drawer">$fas_info-circle</v-icon> </div>
      <slot name="content"></slot>

		</div>
	</div>
</template>

<script>
export default {
  name: 'BannerHorizontal'
}
</script>

<style scoped >

.bg{
	background: var(--hexColor27);
	border: 1px solid var(--lighten2);
	border-radius: 12px;
	padding: 16px;
}

.col_2 {
	width: 100%;
	color: var(--themeColorDark);
	position: relative;
	top: 0px;
	left: 0px;
	font-family: Poppins;
	font-weight: 500;
	font-size: 14px;
	opacity: 1;
	text-align: left;
}

.col_3 {
	width: 120px;
	color: var(--rgbaColor7);
	position: relative;
	top: 0px;
	left: 0px;
	font-family: Poppins;
	/* font-size: 14px; */
	opacity: 1;
	text-align: center;
}

.icon-nav-drawer{
  justify-content: center;
  margin-top: 2px !important;
  margin-bottom: 2px !important;
  color: var(--themeColor);
  height: 16.67px;
  width: 16.67px;
}
</style>