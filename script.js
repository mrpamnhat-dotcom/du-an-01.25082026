const vocabulary = {
    animals: [
        { english: "Dog", vietnamese: "Con chó", emoji: "🐶" },
        { english: "Cat", vietnamese: "Con mèo", emoji: "🐱" },
        { english: "Elephant", vietnamese: "Con voi", emoji: "🐘" }
    ],

    fruits: [
        { english: "Apple", vietnamese: "Quả táo", emoji: "🍎" },
        { english: "Banana", vietnamese: "Quả chuối", emoji: "🍌" },
        { english: "Orange", vietnamese: "Quả cam", emoji: "🍊" }
    ],

    colors: [
        { english: "Red", vietnamese: "Màu đỏ", emoji: "🔴" },
        { english: "Blue", vietnamese: "Màu xanh dương", emoji: "🔵" },
        { english: "Green", vietnamese: "Màu xanh lá", emoji: "🟢" }
    ]
};

function showTopic(topic) {
    const wordArea = document.getElementById("word-area");

    wordArea.innerHTML = "";

    vocabulary[topic].forEach(word => {
        wordArea.innerHTML += `
            <div class="word-card">
                <div class="emoji">${word.emoji}</div>
                <h3>${word.english}</h3>
                <p>${word.vietnamese}</p>
                <button onclick="speakWord('${word.english}')">
                    🔊 Listen
                </button>
            </div>
        `;
    });
}

function speakWord(word) {
    const speech = new SpeechSynthesisUtterance(word);
    speech.lang = "en-US";
    speechSynthesis.speak(speech);
}