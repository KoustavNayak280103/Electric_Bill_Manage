const chatBox = document.getElementById("chat-box");
const userInput = document.getElementById("user-input");
const sendBtn = document.getElementById("send-btn");

// Add message to chat
function addMessage(message, sender) {
  const div = document.createElement("div");
  div.classList.add("message", sender);
  div.textContent = message;
  chatBox.appendChild(div);
  chatBox.scrollTop = chatBox.scrollHeight;
}

// Rule-based responses
function getBotResponse(input) {
  input = input.toLowerCase();

  if (input.includes("hello") || input.includes("hi")) {
    return "Hello! How can I help you today?";
  } else if (input.includes("how are you")) {
    return "I'm just a bot 🤖, but I'm doing great! Thanks for asking.";
  } else if (input.includes("time")) {
    return "The current time is " + new Date().toLocaleTimeString();
  } else if (input.includes("date")) {
    return "Today's date is " + new Date().toLocaleDateString();
  } else if (input.includes("weather")) {
    return "I can't check the weather right now 🌤, but it looks nice outside!";
  } else if (input.includes("bye")) {
    return "Goodbye! Have a nice day 👋";
  } else {
    return "I'm not sure how to respond to that. 🤔";
  }
}

// Send message
sendBtn.addEventListener("click", () => {
  const message = userInput.value.trim();
  if (!message) return;

  addMessage(message, "user");
  userInput.value = "";

  setTimeout(() => {
    const response = getBotResponse(message);
    addMessage(response, "bot");
  }, 500); 
});

userInput.addEventListener("keypress", (e) => {
  if (e.key === "Enter") {
    sendBtn.click();
  }
});