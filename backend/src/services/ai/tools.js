/**
 * OpenAI Function Tool definitions for JARVIS Assistant.
 * Includes base system tools and controlled Accessibility UI automation tools.
 */
export const JARVIS_TOOLS = [
  // --- Safe Base Tools ---
  {
    type: 'function',
    function: {
      name: 'get_time',
      description: 'Returns the current device time.',
      parameters: {
        type: 'object',
        properties: {},
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'get_date',
      description: 'Returns the current device date.',
      parameters: {
        type: 'object',
        properties: {},
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'go_home',
      description: 'Navigates back to the Android home screen.',
      parameters: {
        type: 'object',
        properties: {},
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'press_back',
      description: 'Simulates the Android back navigation action.',
      parameters: {
        type: 'object',
        properties: {},
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'open_url',
      description: 'Opens a validated HTTP or HTTPS web URL in the default Android browser.',
      parameters: {
        type: 'object',
        properties: {
          url: {
            type: 'string',
            description: 'The web URL to open. Must begin with http:// or https://'
          }
        },
        required: ['url'],
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'open_app',
      description: 'Opens an installed application on the Android device by app name or package identifier.',
      parameters: {
        type: 'object',
        properties: {
          appName: {
            type: 'string',
            description: 'The common name or package name of the app to launch (e.g. "YouTube", "Settings", "Chrome", "Camera").'
          }
        },
        required: ['appName'],
        additionalProperties: false
      }
    }
  },

  // --- Accessibility UI Automation Tools ---
  {
    type: 'function',
    function: {
      name: 'read_visible_screen',
      description: 'Inspects and returns a sanitized summary of visible UI controls on the active Android screen.',
      parameters: {
        type: 'object',
        properties: {},
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'click_text',
      description: 'Clicks a visible UI element whose displayed text or content description matches the requested text.',
      parameters: {
        type: 'object',
        properties: {
          text: {
            type: 'string',
            description: 'The displayed text or content label of the control to tap.'
          }
        },
        required: ['text'],
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'click_view',
      description: 'Clicks a visible UI element matching the exact Android view resource ID.',
      parameters: {
        type: 'object',
        properties: {
          viewId: {
            type: 'string',
            description: 'The exact resource view ID (e.g. "com.android.settings:id/switch_widget").'
          }
        },
        required: ['viewId'],
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'type_text',
      description: 'Inputs text into the currently focused editable field (requires user confirmation).',
      parameters: {
        type: 'object',
        properties: {
          text: {
            type: 'string',
            description: 'The text content to input into the focused field.'
          }
        },
        required: ['text'],
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'scroll',
      description: 'Scrolls the currently visible scrollable container forward or backward.',
      parameters: {
        type: 'object',
        properties: {
          direction: {
            type: 'string',
            enum: ['forward', 'backward'],
            description: 'Direction to scroll ("forward" or "backward").'
          }
        },
        required: ['direction'],
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'analyze_current_screen',
      description: 'Captures and visually analyzes the user\'s current foreground screen when explicitly requested (e.g. "What is on my screen?", "Find the settings button", "Where is the login button?"). Combines visual screenshot and sanitized accessibility metadata.',
      parameters: {
        type: 'object',
        properties: {
          focus: {
            type: 'string',
            description: 'Optional focus of what to look for or analyze on screen (e.g. "settings icon", "search bar", "summary")'
          }
        },
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'wait_for_screen',
      description: 'Waits for a target package or text to appear on the foreground screen with bounded timeout.',
      parameters: {
        type: 'object',
        properties: {
          expectedPackage: {
            type: 'string',
            description: 'The expected Android package name (e.g. "com.google.android.youtube").'
          },
          expectedText: {
            type: 'string',
            description: 'Optional expected text to appear on screen.'
          },
          timeoutMs: {
            type: 'number',
            description: 'Maximum milliseconds to wait (max 5000ms).'
          }
        },
        additionalProperties: false
      }
    }
  }
];

export const APPROVED_TOOL_NAMES = new Set(
  JARVIS_TOOLS.map(t => t.function.name)
);
