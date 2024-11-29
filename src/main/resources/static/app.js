document.addEventListener('DOMContentLoaded', function() {
    // Load documents on page load
    loadDocuments();

    // Handle document upload
    document.getElementById('uploadForm').addEventListener('submit', function(e) {
        e.preventDefault();
        uploadDocument();
    });

    // Handle question submission
    document.getElementById('questionForm').addEventListener('submit', function(e) {
        e.preventDefault();
        askQuestion();
    });
});

async function loadDocuments() {
    try {
        const response = await fetch('/api/documents');
        const documents = await response.json();
        
        const documentList = document.getElementById('documentList');
        documentList.innerHTML = '';
        
        documents.forEach(doc => {
            const item = document.createElement('a');
            item.href = '#';
            item.className = 'list-group-item list-group-item-action';
            item.innerHTML = `
                <div class="d-flex w-100 justify-content-between">
                    <h5 class="mb-1">${doc.title}</h5>
                    <small>${new Date(doc.createdAt).toLocaleDateString()}</small>
                </div>
                <p class="mb-1">${doc.content.substring(0, 100)}...</p>
            `;
            documentList.appendChild(item);
        });
    } catch (error) {
        console.error('Error loading documents:', error);
        alert('Error loading documents');
    }
}

async function uploadDocument() {
    const titleInput = document.getElementById('title');
    const fileInput = document.getElementById('file');
    
    const formData = new FormData();
    formData.append('title', titleInput.value);
    formData.append('file', fileInput.files[0]);

    try {
        const response = await fetch('/api/documents', {
            method: 'POST',
            body: formData
        });

        if (response.ok) {
            alert('Document uploaded successfully');
            titleInput.value = '';
            fileInput.value = '';
            loadDocuments();
        } else {
            throw new Error('Upload failed');
        }
    } catch (error) {
        console.error('Error uploading document:', error);
        alert('Error uploading document');
    }
}

async function askQuestion() {
    const questionInput = document.getElementById('question');
    const answerDiv = document.getElementById('answer');
    
    try {
        const response = await fetch('/api/qa/ask', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                question: questionInput.value
            })
        });

        const data = await response.json();
        
        answerDiv.innerHTML = `
            <strong>Q: ${data.question}</strong>
            <p class="mt-2">A: ${data.answer}</p>
        `;
        answerDiv.classList.add('show');
        
        questionInput.value = '';
    } catch (error) {
        console.error('Error asking question:', error);
        alert('Error asking question');
    }
}
