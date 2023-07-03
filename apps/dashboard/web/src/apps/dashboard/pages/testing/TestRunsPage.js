import GithubTable from "../../components/tables/GithubTable"
import {
  IndexTable,
  LegacyCard,
  IndexFilters,
  useSetIndexFiltersMode,
  IndexFiltersMode,
  useIndexResourceState,
  Text,
  ChoiceList,
  Badge,
  Button,
  VerticalStack,
  HorizontalStack,
  ButtonGroup,
  Icon,
  Box
} from '@shopify/polaris';
import {
  HorizontalDotsMinor,
  CircleCancelMinor,
  CircleTickMinor,
  CalendarMinor,
  MagicMinor,
  FraudProtectMinor,
  PlayMinor,
  ClockMinor
} from '@shopify/polaris-icons';

let headers = [
  {
    icon: {
      text: "",
      value: "icon",
      row_order: 0,
    },
    name: {
      text: "Test name",
      value: "name",
      item_order: 0,

    }
  },
  {
    severityList: {
      text: 'Severity',
      value: 'severity',
      item_order: 1,
    }
  },
  {
    icon: {
      text: "",
      value: "",
      row_order: 0,
    },
    details: [
      {
        text: "Number of tests",
        value: "number_of_tests_str",
        item_order: 2,
        icon: FraudProtectMinor,
      },
      {
        text: 'Run type',
        value: 'run_type',
        item_order: 2,
        icon: PlayMinor
      },
      {
        text: 'Run time',
        value: 'run_time',
        item_order: 2,
        sortKey: 'run_time_epoch',
        icon: ClockMinor
      },
    ]
  }
]

let testRuns = [
  {
    hexId: "something",
    icon: CircleTickMinor,
    orderPriority: 1,
    name: 'Bola on Login',
    number_of_tests_str: '10 Tests',
    run_type: 'One time',
    run_time: "Next run in 2 days",
    run_time_epoch: 140,
    scheduleTimestamp: 100,
    severity: [
      {
        confidence: 'High',
        count: '12'
      },
      {
        confidence: 'Medium',
        count: '12'
      },
      {
        confidence: 'Low',
        count: '12'
      }
    ],
    total_severity: 4500,
    severityStatus: ["High", "Medium", "Low"],
    runTypeStatus: ["One-time"],
    severityTags: new Set(["High", "Medium"]),
  },
  {
    hexId: "somethingElse",
    icon: CircleTickMinor,
    orderPriority: 2,
    name: 'All OWASP in staging',
    number_of_tests_str: '100 Tests',
    run_type: 'CI/CD',
    run_time: "Last run 10 mins ago",
    run_time_epoch: 20,
    scheduleTimestamp: 200,
    severity: [
      {
        confidence: 'High',
        count: '12'
      },
      {
        confidence: 'Low',
        count: '12'
      }
    ],
    total_severity: 720,
    severityStatus: ["High", "Low"],
    runTypeStatus: ["CI/CD"],
    severityTags: new Set(["High", "Medium", "Low"]),
  },
  {
    hexId: "somethingElse2",
    icon: CircleTickMinor,
    orderPriority: 2,
    name: 'NoAuth',
    number_of_tests_str: '100 Tests',
    run_type: 'CI/CD',
    run_time: "Last ran 120 mins ago",
    run_time_epoch: 20,
    scheduleTimestamp: 300,
    severity: [
      {
        confidence: 'High',
        count: '12'
      },
      {
        confidence: 'Medium',
        count: '12'
      }
    ],
    total_severity: 1000,
    severityStatus: ["High", "Medium"],
    runTypeStatus: ["CI/CD"],
    severityTags: new Set(["High", "Medium", "Low"]),
  },
]

const sortOptions = [
  { label: 'Severity', value: 'severity asc', directionLabel: 'Highest severity', sortKey: 'total_severity' },
  { label: 'Severity', value: 'severity desc', directionLabel: 'Lowest severity', sortKey: 'total_severity' },
  { label: 'Run time', value: 'time asc', directionLabel: 'Newest run', sortKey: 'scheduleTimestamp' },
  { label: 'Run time', value: 'time desc', directionLabel: 'Oldest run', sortKey: 'scheduleTimestamp' },
];

const resourceName = {
  singular: 'Test run',
  plural: 'Test runs',
};

const filters = [
  {
    key: 'severityStatus',
    label: 'Severity',
    title: 'Severity',
    choices: [
      { label: 'High', value: 'High' },
      { label: 'Medium', value: 'Medium' },
      { label: 'Low', value: 'Low' },
    ]
  },
  {
    key: 'runTypeStatus',
    label: 'Run type',
    title: 'Run type',
    choices: [
      { label: 'CI/CD', value: 'CI/CD' },
      { label: 'One-time', value: 'One-time' },
      { label: 'Recurring', value: 'Recurring' },
    ]
  }
]

function disambiguateLabel(key, value) {
  switch (key) {
    case 'severityStatus':
      return (value).map((val) => `${val} severity`).join(', ');
    case 'runTypeStatus':
      return (value).map((val) => `${val}`).join(', ');
    default:
      return value;
  }
}

function TestRunsPage() {
  return (
    <VerticalStack gap="4">
      <HorizontalStack align="space-between" blockAlign="center">
        <Text as="div" variant="headingLg">
          Test results
        </Text>
        <Button primary>New test run</Button>
      </HorizontalStack>
      <GithubTable testRuns={testRuns} sortOptions={sortOptions} resourceName={resourceName} filters={filters} disambiguateLabel={disambiguateLabel} headers={headers} />
    </VerticalStack>
  );
}

export default TestRunsPage