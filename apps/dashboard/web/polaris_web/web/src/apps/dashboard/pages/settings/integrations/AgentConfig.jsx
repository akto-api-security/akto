import { Button, Combobox, LegacyCard, Listbox, Modal, ResourceItem, ResourceList, Text, TextField, VerticalStack } from "@shopify/polaris";
import IntegrationsLayout from "./IntegrationsLayout";
import { useEffect, useState } from "react";
import Dropdown from "../../../components/layouts/Dropdown";
import api from "../../../pages/agent_team/api";
import func from "../../../../../util/func";
import { usePermissions, whenAllowed } from "@/util/permissions";
import AllowedAction from "../../../components/shared/AllowedAction";

const MODEL_TYPES = {
  ANTHROPIC: "ANTHROPIC",
  OPENAI: "OPENAI",
  AZURE_OPENAI: "AZURE_OPENAI",
  OLLAMA: "OLLAMA",
  DATABRICKS: "DATABRICKS",
  GITHUB_COPILOT: "GITHUB_COPILOT",
  GEMINI: "GEMINI",
  CLOUDFLARE: "CLOUDFLARE",
  VERTEX_AI: "VERTEX_AI",
  BEDROCK: "BEDROCK"
}

const OPENAI_MODELS = [
  { label: "GPT 4o", value: "gpt-4o-2024-08-06" },
  { label: "GPT 4o mini", value: "gpt-4o-mini-2024-07-18" },
  { label: "GPT 5", value: "gpt-5" },
  { label: "GPT 5 mini", value: "gpt-5-mini" },
  { label: "GPT 5 nano", value: "gpt-5-nano" },
  { label: "GPT 5.1", value: "gpt-5.1" },
  { label: "GPT 5.2", value: "gpt-5.2" },
  { label: "GPT 5.2 Pro", value: "gpt-5.2-pro" },
  { label: "GPT 5.4", value: "gpt-5.4" },
  { label: "GPT 5.4 mini", value: "gpt-5.4-mini" },
  { label: "GPT 5.4 nano", value: "gpt-5.4-nano" },
]

const ANTHROPIC_MODELS = [
  { label: "Claude 3.5 Haiku", value: "claude-3-5-haiku-20241022" },
  { label: "Claude 3 Haiku", value: "claude-3-haiku-20240307" },
  { label: "Claude 3.7 Sonnet", value: "claude-3-7-sonnet-20250219" },
  { label: "Claude 3.5 Sonnet", value: "claude-3-5-sonnet-20241022" }
]


const OLLAMA_MODELS = [
  { label: "Qwen small-qwen2.5:0.5b", value: "qwen2.5:0.5b" },
  { label: "Qwen latest-qwen3:8b", value: "qwen3:8b" },
  { label: "Qwen 3 latest-qwen3:latest", value: "qwen3:latest" }
]

const DATABRICKS_MODELS = [
  { label: "Databricks GPT 5.4", value: "databricks-gpt-5-4" },
  { label: "Databricks GPT 5", value: "databricks-gpt-5" },
  { label: "Databricks GPT 5 Nano", value: "databricks-gpt-5-nano" },
  { label: "Databricks Claude Opus 4.6", value: "databricks-claude-opus-4-6" },
  { label: "Databricks Claude Sonnet 4.6", value: "databricks-claude-sonnet-4-6" },
  { label: "Databricks Gemini 2.5 Pro", value: "databricks-gemini-2-5-pro" },
  { label: "Databricks Gemini 2.5 Flash", value: "databricks-gemini-2-5-flash" },
  { label: "Databricks BGE Large EN", value: "databricks-bge-large-en" },
  { label: "Databricks Meta Llama 3.1 8B Instruct", value: "databricks-meta-llama-3-1-8b-instruct" },
  { label: "Databricks Gemma 3 12B", value: "databricks-gemma-3-12b" },
  { label: "Databricks Qwen3 Next 80B A3B Instruct", value: "databricks-qwen3-next-80b-a3b-instruct" },
]

// GitHub Copilot Models - Famous models available through Copilot
const GITHUB_COPILOT_MODELS = [
  { label: "Claude Sonnet 4.5", value: "claude-sonnet-4.5" },
  { label: "Claude Sonnet 4.6", value: "claude-sonnet-4.6" },
  { label: "Claude Haiku 4.5", value: "claude-haiku-4.5" },
  { label: "GPT-4.1", value: "gpt-4.1" },
  { label: "GPT-4o", value: "gpt-4o" },
  { label: "GPT-4o mini", value: "gpt-4o-mini" },
  { label: "GPT-5 mini", value: "gpt-5-mini" },
  { label: "GPT-5.2", value: "gpt-5-2" },
  { label: "GPT-5.4", value: "gpt-5-4" },
  { label: "Gemini 2.5 Pro", value: "gemini-2-5-pro" },
  { label: "Gemini 3.1 Pro (Preview)", value: "gemini-3-1-pro-preview" },
  { label: "Gemini 3 Flash (Preview)", value: "gemini-3-flash-preview" },
  { label: "Grok Code Fast 1", value: "grok-code-fast-1" },
  { label: "Raptor mini (Preview)", value: "raptor-mini-preview" },
]

const GEMINI_MODELS = [
  { label: "Gemini 2.5 Flash", value: "gemini-2.5-flash" },
  { label: "Gemini 2.5 Flash-Lite", value: "gemini-2.5-flash-lite" },
  { label: "Gemini 3.5 Flash", value: "gemini-3.5-flash" },
  { label: "Gemini 3.1 Flash-Lite", value: "gemini-3.1-flash-lite" },
  { label: "Gemini 2.5 Pro", value: "gemini-2-5-pro" },
  { label: "Gemini 3.1 Pro (Preview)", value: "gemini-3-1-pro-preview" },
  { label: "Gemini 3 Flash (Preview)", value: "gemini-3-flash-preview" },
]

// Known model ids offered as suggestions; any other id can still be typed.
const MODEL_SUGGESTIONS = {
  [MODEL_TYPES.OPENAI]: OPENAI_MODELS,
  [MODEL_TYPES.AZURE_OPENAI]: OPENAI_MODELS,
  [MODEL_TYPES.ANTHROPIC]: ANTHROPIC_MODELS,
  [MODEL_TYPES.OLLAMA]: OLLAMA_MODELS,
  [MODEL_TYPES.DATABRICKS]: DATABRICKS_MODELS,
  [MODEL_TYPES.GITHUB_COPILOT]: GITHUB_COPILOT_MODELS,
  [MODEL_TYPES.GEMINI]: GEMINI_MODELS,
}

const MODEL_PLACEHOLDERS = {
  [MODEL_TYPES.ANTHROPIC]: "e.g. claude-sonnet-4-6",
  [MODEL_TYPES.OPENAI]: "e.g. gpt-5-mini",
  [MODEL_TYPES.AZURE_OPENAI]: "Deployment name, e.g. gpt-4o or google--gemma-4-e2b-it",
  [MODEL_TYPES.OLLAMA]: "e.g. qwen3:8b",
  [MODEL_TYPES.DATABRICKS]: "e.g. databricks-claude-sonnet-4-6",
  [MODEL_TYPES.GITHUB_COPILOT]: "e.g. claude-sonnet-4.6",
  [MODEL_TYPES.GEMINI]: "e.g. gemini-2.5-flash",
  [MODEL_TYPES.CLOUDFLARE]: "e.g. @cf/meta/llama-3.3-70b-instruct-fp8-fast",
  [MODEL_TYPES.VERTEX_AI]: "e.g. gemma-3-27b-it",
  [MODEL_TYPES.BEDROCK]: "e.g. us.anthropic.claude-haiku-4-5-20251001-v1:0",
}

// Red teaming has a default model for these providers, so the model may be left empty.
const MODEL_OPTIONAL_TYPES = [MODEL_TYPES.ANTHROPIC, MODEL_TYPES.BEDROCK, MODEL_TYPES.VERTEX_AI]

const RESERVED_NAMES_HELP = "Name a model \"smart-testing\" to use it for smart testing, or \"red-teaming\" to use it for red teaming."

function getApiKeyField(type) {
  let title = "API Key"
  let placeholder = "API Key for the model"
  let multiline = false
  if (type === MODEL_TYPES.OLLAMA) {
    title = "API Key (Optional)"
    placeholder = "API Key for the model (optional)"
  } else if (type === MODEL_TYPES.CLOUDFLARE) {
    placeholder = "Cloudflare API token"
  } else if (type === MODEL_TYPES.VERTEX_AI) {
    title = "Service account JSON"
    placeholder = "Contents of the Google service-account key file"
    multiline = true
  } else if (type === MODEL_TYPES.BEDROCK) {
    title = "Bedrock API Key (Optional)"
    placeholder = "Leave empty to use the service's own AWS credentials (e.g. an IAM role)"
  }
  return { title, id: "apiKey", placeholder, multiline }
}

/*
 * Free-text model field with the provider's known models as suggestions: typing filters
 * the list, picking an entry fills it in, and any other typed id is kept as-is.
 */
function ModelComboField({ label, placeholder, helpText, value, suggestions, onChange }) {
  const text = value || ""
  const query = text.trim().toLowerCase()
  // Show everything when empty or when the value is exactly a listed id (just picked).
  const showAll = !query || suggestions.some((s) => s.value.toLowerCase() === query)
  const options = showAll
    ? suggestions
    : suggestions.filter((s) => s.value.toLowerCase().includes(query) || s.label.toLowerCase().includes(query))

  return (
    <Combobox
      activator={
        <Combobox.TextField
          label={label}
          placeholder={placeholder}
          helpText={helpText}
          value={text}
          onChange={onChange}
          autoComplete="off"
        />
      }
    >
      {options.length > 0 ? (
        <Listbox onSelect={onChange}>
          {options.map((option) => (
            <Listbox.Option key={option.value} value={option.value} selected={option.value === text}>
              {`${option.label} (${option.value})`}
            </Listbox.Option>
          ))}
        </Listbox>
      ) : null}
    </Combobox>
  )
}

function getModelSections(type, data, setData) {
  let sections = []

  sections.push({
    title: "Name",
    id: "name",
    placeholder: "Model name",
    helpText: RESERVED_NAMES_HELP,
  })

  sections.push(getApiKeyField(type))

  const modelOptional = MODEL_OPTIONAL_TYPES.includes(type)
  sections.push({
    title: modelOptional ? "Model (Optional)" : "Model",
    id: "model",
    suggestions: MODEL_SUGGESTIONS[type] || [],
    placeholder: MODEL_PLACEHOLDERS[type],
    helpText: modelOptional ? "If empty, red teaming uses its default model. Smart testing needs a model." : undefined,
  })

  sections.push({
    title: "Fast model (Optional)",
    id: "fastModel",
    suggestions: MODEL_SUGGESTIONS[type] || [],
    placeholder: "Cheaper/faster model id for quick sub-tasks",
    helpText: "Used by red teaming for its quick steps; defaults to the model above.",
  })

  switch (type) {
    case MODEL_TYPES.CLOUDFLARE:
      sections.push({
        title: "Cloudflare Account ID",
        id: "cloudflareAccountId",
        placeholder: "Your Cloudflare account ID",
      })
      break;
    case MODEL_TYPES.DATABRICKS:
      sections.push({
        title: "Databricks Model Serving Endpoint or AI Gateway Endpoint",
        id: "databricksEndpoint",
        placeholder: "The URL for your Databricks model serving or AI Gateway endpoint",
      })
      break;
    case MODEL_TYPES.AZURE_OPENAI:
      sections.push({
        title: "Azure OpenAI Endpoint",
        id: "azureOpenAIEndpoint",
        placeholder: "The base URL for your Azure OpenAI resource",
      })
      break;
    case MODEL_TYPES.OLLAMA:
      sections.push({
        title: "OLLAMA Server Endpoint",
        id: "ollamaAIEndpoint",
        placeholder: "The base URL for your OLLAMA server",
      })
      break;
    case MODEL_TYPES.VERTEX_AI:
      sections.push(
        { title: "GCP Project ID", id: "vertexProjectId", placeholder: "e.g. my-gcp-project" },
        { title: "Location", id: "vertexLocation", placeholder: "e.g. us-central1" },
        { title: "Endpoint ID", id: "vertexEndpointId", placeholder: "Vertex AI endpoint ID serving the model" },
        {
          title: "Dedicated endpoint domain (Optional)",
          id: "vertexEndpointDomain",
          placeholder: "e.g. 1234567890.us-central1-1234.prediction.vertexai.goog",
        },
      )
      break;
    case MODEL_TYPES.BEDROCK:
      sections.push({ title: "AWS Region", id: "awsRegion", placeholder: "e.g. us-east-1" })
      break;
    default:
      break;
  }

  for (let section of sections) {
    const setValue = (value) => setData({ ...data, [section.id]: value })
    section.component = section.suggestions ? (
      <ModelComboField
        key={`${section.id}-${type}`}
        label={section.title}
        placeholder={section.placeholder}
        helpText={section.helpText}
        value={data[section.id]}
        suggestions={section.suggestions}
        onChange={setValue}
      />
    ) : (
      <TextField
        key={section.id}
        label={section.title}
        placeholder={section.placeholder}
        multiline={section.multiline ? 4 : undefined}
        helpText={section.helpText}
        value={data[section.id]}
        onChange={setValue}
      />
    )
  }

  return sections

}

function AgentConfig() {

  const { canCall } = usePermissions()
  let cardContent = "Configure agents to unlock the magical power of LLMs and make your security team more efficient. Akto's agents can be used to look for false positives, analyze traffic and much more."

  const [addModelPopOverActive, setAddModelPopOverActive] = useState(false)
  const [data, setData] = useState({})
  const [modelType, setModelType] = useState(MODEL_TYPES.OPENAI)
  const [modelList, setModelList] = useState([])

  async function fetchModels() {
    await api.getAgentModels().then((res) => {
      if (res && res.models) {
        setModelList(res.models);
      }
    })
  }

  useEffect(() => {
    fetchModels()
  }, [addModelPopOverActive])

  async function deleteModel(name) {
    await api.deleteAgentModel({ name })
    func.setToast(true, false, "Successfully deleted model")
    await fetchModels()
  }


  function renderItem(item) {
    const { name, type } = item;
    return (
      <ResourceItem id={name}
        shortcutActions={[
          {
            content: "Delete",
            destructive: true,
            onClick: () => deleteModel(name),
            ...whenAllowed(canCall('api/deleteAgentModel')),
          },
        ]}
        persistActions
      >
        <Text fontWeight="medium">
          {`${name} ( ${type._name} )`}
        </Text>
      </ResourceItem>
    );
  }

  const Card = (
    <LegacyCard>
      {
        modelList.length > 0 ?
          <ResourceList
            resourceName={{ singular: 'model', plural: 'models' }}
            showHeader={true}
            renderItem={renderItem}
            items={modelList}
          /> : <LegacyCard.Section>
            <Text color="subdued">
              No models available
            </Text>
          </LegacyCard.Section>
      }
      <Modal
        open={addModelPopOverActive}
        onClose={() => {
          setData({})
          setAddModelPopOverActive(false)
        }}
        title="Add model"
        primaryAction={{
          content: 'Add',
          onAction: async () => {
            await api.saveAgentModel({ type: modelType, ...data })
            setData({})
            func.setToast(true, false, "Successfully added model")
            setAddModelPopOverActive(false)
          },
          ...whenAllowed(canCall('api/saveAgentModel')),
        }}
        secondaryActions={[
          {
            content: 'Cancel',
            onAction: () => {
              setData({})
              setAddModelPopOverActive(false)
            },
          },
        ]}
      >
        <Modal.Section>
          <Dropdown
            id={`model-type`}
            key={`model-type`}
            menuItems={[{
              label: 'OpenAI',
              value: MODEL_TYPES.OPENAI
            },
            {
              label: 'Anthropic',
              value: MODEL_TYPES.ANTHROPIC
            },
            {
              label: 'Azure OpenAI',
              value: MODEL_TYPES.AZURE_OPENAI
            },
            {
              label: 'OLLAMA',
              value: MODEL_TYPES.OLLAMA
            },
            {
              label: 'Databricks',
              value: MODEL_TYPES.DATABRICKS
            },
            {
              label: 'GitHub Copilot',
              value: MODEL_TYPES.GITHUB_COPILOT
            },
            {
              label: 'Gemini',
              value: MODEL_TYPES.GEMINI
            },
            {
              label: 'Cloudflare',
              value: MODEL_TYPES.CLOUDFLARE
            },
            {
              label: 'Vertex AI',
              value: MODEL_TYPES.VERTEX_AI
            },
            {
              label: 'AWS Bedrock',
              value: MODEL_TYPES.BEDROCK
            }
            ]}
            initial={modelType}
            selected={(value) => {
              setModelType(value)
              setData({})
            }} />
        </Modal.Section>
        <Modal.Section>
          <VerticalStack gap="2">
            {
              getModelSections(modelType, data, setData).map((section) => {
                return section.component
              })
            }
          </VerticalStack>
        </Modal.Section>
      </Modal>
    </LegacyCard>
  )

  const secondaryAction = (
    <AllowedAction allowed={canCall('api/saveAgentModel')}>
    <Button onClick={() => {
      setData({})
      setAddModelPopOverActive(true)
    }} primary>
      Add model
    </Button>
    </AllowedAction>
  )

  return (
    <IntegrationsLayout title="Agents configuration"
      cardContent={cardContent}
      component={Card}
      secondaryAction={secondaryAction}
      readOnly={!canCall('api/saveAgentModel')}
    />
  )
}

export default AgentConfig;
