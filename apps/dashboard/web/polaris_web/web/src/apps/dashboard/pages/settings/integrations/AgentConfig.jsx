import { Button, LegacyCard, Modal, ResourceItem, ResourceList, Text, TextField, VerticalStack } from "@shopify/polaris";
import IntegrationsLayout from "./IntegrationsLayout";
import { useEffect, useState } from "react";
import Dropdown from "../../../components/layouts/Dropdown";
import api from "../../../pages/agent_team/api";
import func from "../../../../../util/func";

const MODEL_TYPES = {
  ANTHROPIC: "ANTHROPIC",
  OPENAI: "OPENAI",
  AZURE_OPENAI: "AZURE_OPENAI",
}

const OPENAI_MODELS = [
  { label: "GPT 4o", value: "gpt-4o-2024-08-06" },
  { label: "GPT 4o mini", value: "gpt-4o-mini-2024-07-18" }
]

const ANTHROPIC_MODELS = [
  { label: "Claude 3.5 Haiku", value: "claude-3-5-haiku-20241022" },
  { label: "Claude 3 Haiku", value: "claude-3-haiku-20240307" },
  { label: "Claude 3.7 Sonnet", value: "claude-3-7-sonnet-20250219" },
  { label: "Claude 3.5 Sonnet", value: "claude-3-5-sonnet-20241022" }
]

function getModelSections(type, data, setData) {
  let sections = []

  sections.push({
    title: "Name",
    type: "text",
    id: "name",
    placeholder: "Model name",
  }, {
    title: "API Key",
    type: "text",
    id: "apiKey",
    placeholder: "API Key for the model",
  },{
    title: "Model",
    type: "dropdown",
    id: "model"
  })
  switch (type) {
    case MODEL_TYPES.ANTHROPIC:
    case MODEL_TYPES.OPENAI:
      break;
    case MODEL_TYPES.AZURE_OPENAI:
      sections.push({
        title: "Azure OpenAI Endpoint",
        type: "text",
        id: "azureOpenAIEndpoint",
        placeholder: "The base URL for your Azure OpenAI resource",
      })
      break;
    default:
      break;
  }

  for (let section of sections) {
    if (section.type === "text") {
      section.component = (
        <TextField
          label={section?.title}
          placeholder={section?.placeholder}
          value={data[section?.id]}
          onChange={(value) => {
            setData({
              ...data,
              [section?.id]: value
            })
          }}
        />
      )
    } else if (section.type === "dropdown") {
      let items = []
      if (type === MODEL_TYPES.OPENAI || type === MODEL_TYPES.AZURE_OPENAI) {
        items = OPENAI_MODELS
      } else if (type === MODEL_TYPES.ANTHROPIC) {
        items = ANTHROPIC_MODELS
      }
      section.component = (
        <VerticalStack gap="1">
          <Text>
            {section?.title}
          </Text>
          <Dropdown
          id={section?.id}
          key={`${section?.id}-${type}`}
          menuItems={items}
          initial={data[section?.id]}
          selected={(value) => {
            setData((x) => {
              return {
              ...x,
              [section?.id]: value
            }})
          }} />
        </VerticalStack>
      )
    }
  }

  return sections

}

function AgentConfig() {

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
    <Button onClick={() => {
      setData({})
      setAddModelPopOverActive(true)
    }} primary>
      Add model
    </Button>
  )

  return (
    <IntegrationsLayout title="Agents configuration"
      cardContent={cardContent}
      component={Card}
      secondaryAction={secondaryAction}
    />
  )
}

export default AgentConfig;