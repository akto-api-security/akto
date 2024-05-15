import Vue from 'vue'
import Vuetify from 'vuetify'
import 'vuetify/dist/vuetify.min.css'
import { library } from '@fortawesome/fontawesome-svg-core' // Core SVG
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome' // Integration
import CurlyBraces from "../apps/dashboard/shared/icons/CurlyBraces"
import Burpsuite from "../apps/dashboard/shared/icons/Burpsuite"
import Slack from "../apps/dashboard/shared/icons/Slack"
import GithubName from "../apps/dashboard/shared/icons/GithubName"
import GithubIcon from "../apps/dashboard/shared/icons/GithubIcon"
import Postman from "../apps/dashboard/shared/icons/Postman"
import AWS from "../apps/dashboard/shared/icons/AWS"
import GCP from "../apps/dashboard/shared/icons/GCP"
import k8s from '../apps/dashboard/shared/icons/k8s'
import FargateIcon from '../apps/dashboard/shared/icons/FargateIcon'
import Swagger from "../apps/dashboard/shared/icons/Swagger"
import Restapi from "../apps/dashboard/shared/icons/Restapi"
import CustomWebhooks from "../apps/dashboard/shared/icons/CustomWebhooks"
import TrashSingleTick from "../apps/dashboard/shared/icons/TrashSingleTick"
import TrashDoubleTick from "../apps/dashboard/shared/icons/TrashDoubleTick"
import AktoWhite from "../apps/dashboard/shared/icons/AktoWhite"
import BookBookmark from "../apps/dashboard/shared/icons/BookBookmark"
import NGINX from "../apps/dashboard/shared/icons/NGINX"
import Discord from "../apps/dashboard/shared/icons/Discord"
import DeleteIcon from "../apps/dashboard/shared/icons/DeleteIcon"
import PlusIcon from "../apps/dashboard/shared/icons/PlusIcon"
import CICDIcon from "../apps/dashboard/shared/icons/CICDIcon"
import ChatGPT from "../apps/dashboard/shared/icons/ChatGPT"
import SaveIcon from "../apps/dashboard/shared/icons/SaveIcon"
import TestEditorIcon from "../apps/dashboard/shared/icons/TestEditorIcon"

Vue.component('font-awesome-icon', FontAwesomeIcon)

import {
  faClipboardList,
  faLongArrowAltDown,
  faLongArrowAltUp,
  faUnlockAlt,
  faStethoscope,
  faBars,
  faUser,
  faPlus,
  faTimes,
  faEnvelope,
  faPhoneAlt,
  faLock,
  faLockOpen,  
  faAngleUp,
  faAngleDown,
  faAngleRight,
  faAngleLeft,
  faComments,
  faEye,
  faEllipsisV,
  faEllipsisH,
  faStar,
  faCalendar,
  faCog,
  faShare,
  faEdit,
  faCheck,
  faTrash,
  faSearch,
  faBell,
  faChartBar,
  faFileAlt,
  faHandPointRight,
  faChartArea,
  faExclamationTriangle,
  faInfoCircle,
  faDownload,
  faFileCsv,
  faCircle,
  faThLarge,
  faStopwatch,
  faCheckCircle,
  faEyeSlash,
  faArrowLeft,
  faUsers,
  faChartLine,
  faHome,
  faBullseye,
  faBullhorn,
  faCheckDouble,
  faSquare,
  faBolt,
  faDatabase,
  faAngleDoubleRight,
  faAngleDoubleLeft,
  faAngleDoubleDown,
  faAngleDoubleUp,
  faSyncAlt,
  faExchangeAlt,
  faUserLock,
  faExclamation,
  faCreditCard,
  faAddressCard,
  faChalkboard,
  faFilter,
  faKey,
  faUpload,
  faRedo,
  faGlobe,
  faUserSecret,
  faCopy,
  faPlay,
  faStop,
  faPen,
  faCalendarPlus,
  faCalendarTimes,
  faCaretSquareUp,
  faThumbsUp,
  faCaretUp,
  faCaretDown,
  faShareAlt,
  faUserPlus,
  faLaptopHouse,
  faAnchor,
  faArrowRight,
  faGift,
  faBook,
  faGripLines,
  faAlignJustify,
  faMagic,
  faLayerGroup,
  faPaperPlane,
  faUserGraduate,
  faArrowUp,
  faExternalLinkAlt
} from '@fortawesome/free-solid-svg-icons'

import {
  faComments as farComments,
  faCalendar as farCalendar,
  faHandPointRight as farHandPointRight,
  faBuilding as farBuilding,
  faStar as farStar,
  faSquare as farSquare,
  faCheckSquare as farCheckSquare,
  faFolder as farFolder,
  faFolderOpen as farFolderOpen,
  faClock as farClock,
  faQuestionCircle as farQuestionCircle,
  faPaperPlane as farPaperPlane,
  faCircle as farCircle,
  faDotCircle as farDotCircle
} from '@fortawesome/free-regular-svg-icons'

import {
  faSalesforce as fabSalesforce,
  faHubspot as fabHubspot,
  faGithub as fabGithub,
  faDiscord as fabDiscord
} from '@fortawesome/free-brands-svg-icons'

const faIcons = [
  faBars,faUser,faPlus,faTimes,faEnvelope,faLock,faLockOpen,faAngleUp,faAngleRight,faAngleLeft,faComments,faEye,faEllipsisV,faStar,faCalendar,
  faCog,faShare,faEdit,faCheck,faTrash,faSearch,faBell,faChartBar,faFileAlt,faHandPointRight,faChartArea,faExclamationTriangle,
  faInfoCircle,faDownload,faFileCsv,faCircle,faThLarge,faStopwatch,faCheckCircle,faEyeSlash,faArrowLeft,faUsers,faChartLine,
  faHome,faBullseye,faBullhorn,faCheckDouble,faSquare,faBolt,faDatabase,faAngleDoubleRight, faAngleDoubleLeft,faClipboardList,
  faUnlockAlt,faStethoscope,faLongArrowAltDown,faLongArrowAltUp,faSyncAlt, faExchangeAlt, faUserLock, faExclamation,faPhoneAlt,
  faCreditCard,faAddressCard,faChalkboard,faFilter,faKey,faAngleDoubleDown,faAngleDoubleUp,faUpload,faRedo,faGlobe,faAngleDown,
  faUserSecret,faCopy,faPlay,faStop,faPen,faCalendarPlus,faCalendarTimes,faCaretSquareUp,faEllipsisH,
  faUserSecret,faCopy,faPlay,faStop,faPen,faCalendarPlus,faCalendarTimes,faThumbsUp,faCaretUp,faShareAlt,faUserPlus,faCaretDown,
  faLaptopHouse,faAnchor,fabGithub,faArrowRight,faGift,faGripLines,faAlignJustify,faBook,faMagic,faLayerGroup,faPaperPlane,faUserGraduate,faArrowUp,
  faExternalLinkAlt,
  
  farComments,farCalendar, farHandPointRight, farBuilding, farStar,farSquare, farCheckSquare,farFolder,farFolderOpen,farClock,
  farQuestionCircle, faBook, farCircle, farDotCircle, farPaperPlane,

  fabSalesforce,fabHubspot,fabDiscord
]

library.add(faIcons)

Vue.use(Vuetify)

const ctTheme = {
  primary: '#126BFF',
  secondary: '#434761',
  secondaryBtn: '#4900AF',
  errorBtn: '#FF5353',
  negativeBtn: '#63698f',
  toggleBtn: '#ECEDF2',
  accent: '#2d2434',
  error: '#FF5252',
  info: '#2196F3',
  success: '#4CAF50',
  warning: '#FFC107',
  base: '#2d2434',
  darken1: '#63698F',
  darken2: '#434761',
  lighten1: '#D0D2E1',
  anchor: '#085ce7',
  redMetric: '#f44336',
  greenMetric: '#00bfa5',
  themeColor: '#6200EA'
}

const faIconsOpts = {}

faIconsOpts.deleteIcon = {
  component: DeleteIcon
}

faIconsOpts.plusIcon = {
  component: PlusIcon
}

faIconsOpts.bookBookmark = {
  component: BookBookmark
}

faIconsOpts.aktoWhite = {
  component: AktoWhite
}

faIconsOpts.curlyBraces = {
  component: CurlyBraces
}

faIconsOpts.burpsuite = {
  component: Burpsuite
}

faIconsOpts.postman = {
  component: Postman
}

faIconsOpts.aws = {
  component: AWS
}

faIconsOpts.k8s = {
  component: k8s
}

faIconsOpts.fargateIcon = {
  component: FargateIcon
}

faIconsOpts.swagger = {
  component: Swagger
}

faIconsOpts.gcp = {
  component: GCP
}

faIconsOpts.slack = {
  component: Slack
}

faIconsOpts.discord = {
  component: Discord
}

faIconsOpts.restapi = {
  component: Restapi
}

faIconsOpts.githubName = {
  component: GithubName
}

faIconsOpts.githubIcon = {
  component: GithubIcon
}

faIconsOpts.customwebhooks = {
  component: CustomWebhooks
}

faIconsOpts.trashSingleTick = {
  component: TrashSingleTick
}

faIconsOpts.trashDoubleTick = {
  component: TrashDoubleTick
}

faIconsOpts.cicdicon = {
  component: CICDIcon

}

faIconsOpts.nginx = {
  component: NGINX
}

faIconsOpts.chatGPT = {
  component: ChatGPT
}

faIconsOpts.saveIcon = {
  component: SaveIcon
}

faIconsOpts.testEditorIcon = {
  component: TestEditorIcon
}

faIcons.forEach(x => faIconsOpts[x.prefix+"_"+x.iconName] = {
  component: FontAwesomeIcon,
  props: {
    icon: [x.prefix, x.iconName]
  }
})

const opts = {
  theme: {
    themes: {
      light: ctTheme,
      dark: ctTheme
    },
	options: { customProperties: true },
  },
  icons: {
    values: faIconsOpts
  }
}

export default new Vuetify(opts)

