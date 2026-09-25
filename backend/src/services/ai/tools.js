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
  {
    type: 'function',
    function: {
      name: 'call_contact',
      description: 'Initiates a phone call to a named contact from the local address book (e.g. "Call Daddy", "Call Mom", "Call Alice"). Performs contact resolution locally on device.',
      parameters: {
        type: 'object',
        properties: {
          contactName: {
            type: 'string',
            description: 'The name or relationship of the contact to call (e.g. "Daddy", "Mom", "John").'
          }
        },
        required: ['contactName'],
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
  },
  // --- Device Control Tools (Phase 1) ---
  {
    type: 'function',
    function: {
      name: 'get_battery_status',
      description: 'Checks the current device battery percentage and charging status (e.g. "How much battery do I have?").',
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
      name: 'set_volume',
      description: 'Sets or adjusts the device volume level (0-100%) or direction ("up", "down", "mute", "unmute").',
      parameters: {
        type: 'object',
        properties: {
          volumePercent: {
            type: 'number',
            description: 'Target volume percentage between 0 and 100.'
          },
          direction: {
            type: 'string',
            enum: ['up', 'down', 'mute', 'unmute'],
            description: 'Relative volume adjustment direction.'
          },
          stream: {
            type: 'string',
            enum: ['music', 'ring', 'alarm', 'call'],
            description: 'Audio stream to adjust (defaults to music).'
          }
        },
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'get_volume',
      description: 'Queries the current volume percentage and mute status of the device.',
      parameters: {
        type: 'object',
        properties: {
          stream: {
            type: 'string',
            enum: ['music', 'ring', 'alarm', 'call'],
            description: 'Audio stream to check.'
          }
        },
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'set_brightness',
      description: 'Sets the device screen brightness percentage (0-100%).',
      parameters: {
        type: 'object',
        properties: {
          brightnessPercent: {
            type: 'number',
            description: 'Target brightness percentage between 0 and 100.'
          }
        },
        required: ['brightnessPercent'],
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'get_brightness',
      description: 'Queries the current device screen brightness percentage.',
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
      name: 'toggle_flashlight',
      description: 'Turns the device flashlight on, off, or toggles state.',
      parameters: {
        type: 'object',
        properties: {
          action: {
            type: 'string',
            enum: ['on', 'off', 'toggle'],
            description: 'Action to perform on flashlight.'
          },
          enabled: {
            type: 'boolean',
            description: 'Explicit desired state (true for on, false for off).'
          }
        },
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'open_camera',
      description: 'Opens the device camera to take photos or record videos.',
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
      name: 'get_device_info',
      description: 'Retrieves device hardware model, Android OS version, and storage/RAM metrics.',
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
      name: 'get_network_status',
      description: 'Checks device network connectivity (Wi-Fi, Mobile Data, or Offline).',
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
      name: 'open_wifi_settings',
      description: 'Opens the Android Wi-Fi settings page.',
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
      name: 'open_bluetooth_settings',
      description: 'Opens the Android Bluetooth settings page.',
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
      name: 'lock_screen',
      description: 'Locks the device screen immediately.',
      parameters: {
        type: 'object',
        properties: {},
        additionalProperties: false
      }
    }
  },
  // --- Media Control Tools (Phase 2) ---
  {
    type: 'function',
    function: {
      name: 'play_media',
      description: 'Starts or resumes media/music playback.',
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
      name: 'pause_media',
      description: 'Pauses active media/music playback.',
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
      name: 'resume_media',
      description: 'Resumes media/music playback.',
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
      name: 'next_track',
      description: 'Skips to the next music track or media item.',
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
      name: 'previous_track',
      description: 'Skips back to the previous music track or media item.',
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
      name: 'get_media_state',
      description: 'Checks if media/music is currently playing.',
      parameters: {
        type: 'object',
        properties: {},
        additionalProperties: false
      }
    }
  },
  // --- SMS Tools (Phase 3) ---
  {
    type: 'function',
    function: {
      name: 'send_sms',
      description: 'Sends an SMS text message to a contact or phone number with explicit user confirmation.',
      parameters: {
        type: 'object',
        properties: {
          contactName: {
            type: 'string',
            description: 'Name of the contact to message (e.g. "Daddy", "Mom"). Resolved locally on device.'
          },
          phoneNumber: {
            type: 'string',
            description: 'Direct phone number to message.'
          },
          message: {
            type: 'string',
            description: 'The body of the text message to send.'
          }
        },
        required: ['message'],
        additionalProperties: false
      }
    }
  },
  // --- Screen Assistant Tools (Phase 6) ---
  {
    type: 'function',
    function: {
      name: 'find_screen_element',
      description: 'Finds and locates a UI control or button on screen by label or query.',
      parameters: {
        type: 'object',
        properties: {
          query: {
            type: 'string',
            description: 'Label, text, or description of the control to find (e.g. "search bar", "login button").'
          }
        },
        required: ['query'],
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'read_current_screen',
      description: 'Reads and summarizes visible content and interactable controls on the active screen.',
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
      name: 'diagnose_screen_error',
      description: 'Detects and explains visible error messages, failure alerts, or crash logs on the current screen.',
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
      name: 'click_screen_element',
      description: 'Clicks a button or control on screen by text label or view ID.',
      parameters: {
        type: 'object',
        properties: {
          text: {
            type: 'string',
            description: 'The visible text or description of the element to click.'
          },
          viewId: {
            type: 'string',
            description: 'The exact resource view ID.'
          }
        },
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'scroll_screen',
      description: 'Scrolls the active screen container.',
      parameters: {
        type: 'object',
        properties: {
          direction: {
            type: 'string',
            enum: ['down', 'up', 'forward', 'backward'],
            description: 'Direction to scroll.'
          }
        },
        additionalProperties: false
      }
    }
  },
  // --- Web Assistant Tools (Phase 7) ---
  {
    type: 'function',
    function: {
      name: 'search_web',
      description: 'Searches Google on the web for a topic or query using the browser.',
      parameters: {
        type: 'object',
        properties: {
          query: {
            type: 'string',
            description: 'The web search query string.'
          }
        },
        required: ['query'],
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'search_youtube',
      description: 'Searches YouTube for videos, tutorials, or music tracks.',
      parameters: {
        type: 'object',
        properties: {
          query: {
            type: 'string',
            description: 'The YouTube search query.'
          }
        },
        required: ['query'],
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'read_current_webpage',
      description: 'Reads and extracts content from the open webpage in the browser.',
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
      name: 'summarize_webpage',
      description: 'Summarizes key highlights from the active webpage.',
      parameters: {
        type: 'object',
        properties: {},
        additionalProperties: false
      }
    }
  },
  // --- Local Notes Tools (Phase 8) ---
  {
    type: 'function',
    function: {
      name: 'create_note',
      description: 'Creates and saves a new personal note on device.',
      parameters: {
        type: 'object',
        properties: {
          title: {
            type: 'string',
            description: 'Title of the note.'
          },
          content: {
            type: 'string',
            description: 'Content of the note.'
          },
          tags: {
            type: 'string',
            description: 'Optional tags.'
          }
        },
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'search_notes',
      description: 'Searches saved personal notes by title or keywords.',
      parameters: {
        type: 'object',
        properties: {
          query: {
            type: 'string',
            description: 'Search keyword or query.'
          }
        },
        required: ['query'],
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'list_notes',
      description: 'Lists all saved personal notes.',
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
      name: 'update_note',
      description: 'Updates or appends content to an existing note.',
      parameters: {
        type: 'object',
        properties: {
          noteId: {
            type: 'number',
            description: 'ID of the note.'
          },
          title: {
            type: 'string',
            description: 'Title of the note.'
          },
          content: {
            type: 'string',
            description: 'New content to append or replace.'
          },
          append: {
            type: 'boolean',
            description: 'True to append content, false to replace.'
          }
        },
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'delete_note',
      description: 'Deletes a note by title or ID (requires confirmation).',
      parameters: {
        type: 'object',
        properties: {
          noteId: {
            type: 'number',
            description: 'ID of the note to delete.'
          },
          title: {
            type: 'string',
            description: 'Title of the note to delete.'
          }
        },
        additionalProperties: false
      }
    }
  },
  // --- Reminders and Timers Tools (Phase 9) ---
  {
    type: 'function',
    function: {
      name: 'create_timer',
      description: 'Sets a countdown timer on device.',
      parameters: {
        type: 'object',
        properties: {
          durationSeconds: {
            type: 'number',
            description: 'Duration in seconds.'
          },
          minutes: {
            type: 'number',
            description: 'Duration in minutes.'
          },
          label: {
            type: 'string',
            description: 'Optional label for the timer.'
          }
        },
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'cancel_timer',
      description: 'Cancels an active timer.',
      parameters: {
        type: 'object',
        properties: {
          timerId: {
            type: 'number',
            description: 'Timer ID to cancel.'
          },
          label: {
            type: 'string',
            description: 'Timer label to cancel.'
          }
        },
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'list_timers',
      description: 'Lists active countdown timers.',
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
      name: 'create_reminder',
      description: 'Schedules a reminder notification.',
      parameters: {
        type: 'object',
        properties: {
          message: {
            type: 'string',
            description: 'Reminder message.'
          },
          relativeMinutes: {
            type: 'number',
            description: 'Minutes from now.'
          },
          triggerTimeMs: {
            type: 'number',
            description: 'Epoch millisecond timestamp.'
          }
        },
        required: ['message'],
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'cancel_reminder',
      description: 'Cancels an existing reminder.',
      parameters: {
        type: 'object',
        properties: {
          reminderId: {
            type: 'number',
            description: 'Reminder ID.'
          },
          query: {
            type: 'string',
            description: 'Reminder message query.'
          }
        },
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'list_reminders',
      description: 'Lists upcoming scheduled reminders.',
      parameters: {
        type: 'object',
        properties: {},
        additionalProperties: false
      }
    }
  },
  // --- Controlled Calendar Tools (Phase 10) ---
  {
    type: 'function',
    function: {
      name: 'create_calendar_event',
      description: 'Creates a calendar event (requires user confirmation).',
      parameters: {
        type: 'object',
        properties: {
          title: {
            type: 'string',
            description: 'Title of the event.'
          },
          date: {
            type: 'string',
            description: 'Date string (e.g. "today", "tomorrow", "2026-09-25").'
          },
          time: {
            type: 'string',
            description: 'Time string (e.g. "7:00 PM", "9 AM").'
          },
          durationMinutes: {
            type: 'number',
            description: 'Duration in minutes (default 60).'
          },
          location: {
            type: 'string',
            description: 'Event location.'
          }
        },
        required: ['title'],
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'list_calendar_events',
      description: 'Lists calendar events for today, tomorrow, or a range.',
      parameters: {
        type: 'object',
        properties: {
          range: {
            type: 'string',
            description: 'Range ("today", "tomorrow", "week").'
          }
        },
        additionalProperties: false
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'delete_calendar_event',
      description: 'Deletes a calendar event by title or ID (requires confirmation).',
      parameters: {
        type: 'object',
        properties: {
          eventId: {
            type: 'number',
            description: 'ID of event to delete.'
          },
          title: {
            type: 'string',
            description: 'Title of event to delete.'
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
