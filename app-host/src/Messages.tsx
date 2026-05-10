import React from "react";
import MUI from "@material-ui/core";
const {
	Button, TextField,
	Dialog, DialogTitle, DialogActions,
	DialogContent, DialogContentText,
} = MUI;

import { rpc } from "./index";

export function Messages(props) {
	const [messages, setMessages] = React.useState([]);
	const [selectedMessage, setSelectedMessage] = React.useState(null);
	const [showDetails, setShowDetails] = React.useState(false);
	
	/*const fetchMessagesFromStorage = () => {
		const storedMessages = localStorage.getItem('contactUsData');
		if (storedMessages) {
			const messagesArray = JSON.parse(storedMessages);
			setMessages(messagesArray);
		} else {
			setMessages([]);
		}
	};*/
	
	React.useEffect(() => {
		rpc.loadMessages().then(setMessages);
	}, []);
	
	const handleMsClick = (message) => {
		setSelectedMessage(message);
		setShowDetails(true);
	};
	
	const closeDetails = ()=>{
		setShowDetails(false);
	};
	
	if (props.messages.length==0)
		return <p style={{ fontSize: "20px", color: "royalBlue", padding: "15px" }}>
			Es gibt keine Nachrichten von Kunden zu sehen.
		</p>
	
	return <>
		<table>
			<caption>Erhaltene Nachrichten von Kunden</caption>
			<thead>
				<tr>
					<th>Name</th>
					<th>Email</th>
					<th>Nachricht</th>
					<th>Datum gesendet</th>
				</tr>
			</thead>
			<tbody>
				{messages.map((message, index) => (
					<tr key={index}>
						<td>{message.name}</td>
						<td>{message.email}</td>
						<td>{message.message}</td>
						<td>{new Date(message.dateSent).toLocaleString()}</td>
					</tr>
				))}
			</tbody>
		</table>
		
		<Button variant="outlined" onClick={handleMsClick} style={{ marginTop: 16 }}>Details anzeigen</Button>
		<Dialog open={showDetails} onClose={closeDetails}>
			<DialogContent>
				{selectedMessage? (
					<>
					<DialogContentText>
						<b><i>Name</i></b>{selectedMessage.name}
					</DialogContentText>
					
					<DialogContentText>
						<b><i>Email</i></b>{selectedMessage.email}
					</DialogContentText>
					
					<DialogContentText>
						<b><i>Nachricht</i></b>{selectedMessage.message}
					</DialogContentText>
					
					
					<DialogContentText>
						<b><i>Datum gesendet:</i></b>{selectedMessage.dateSent}
					</DialogContentText>
					</>
					
					) : (
						<h5>Bitte wählen Sie eine Nachricht aus der Tabelle aus, um die Details hier anzuzeigen.</h5>
					)}
			</DialogContent>
			<DialogActions>
				<Button onClick={closeDetails}>Schließen</Button>
			</DialogActions>
		</Dialog>
	</>
}
