CREATE TABLE inquiries (
    id UUID PRIMARY KEY,
    user_id UUID NULL REFERENCES users(id) ON DELETE SET NULL,
    guest_session_id UUID NULL,
    email VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    title VARCHAR(120) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    responded_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE inquiry_responses (
    id UUID PRIMARY KEY,
    inquiry_id UUID NOT NULL REFERENCES inquiries(id) ON DELETE CASCADE,
    responder_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    content TEXT NOT NULL,
    sent_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_inquiries_user_id_created_at ON inquiries(user_id, created_at DESC);
CREATE INDEX idx_inquiries_status_created_at ON inquiries(status, created_at DESC);
CREATE INDEX idx_inquiries_guest_session_id ON inquiries(guest_session_id);
CREATE INDEX idx_inquiry_responses_inquiry_id_sent_at ON inquiry_responses(inquiry_id, sent_at ASC);
