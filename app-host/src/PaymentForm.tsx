import React from "react";
import './css/PaymentForm.css';

export function PaymentForm({
	onBack,
	onSubmit,
}: {
	onBack: () => void;
	onSubmit: () => void;
}) {
	return (
		<div className="paymentFormOverlay">
			<div className="payment-form">
				<h3>Zahlungsdetails</h3>
				<p>Bitte geben Sie Ihre Zahlungsinformationen ein, um die Buchung abzuschließen.</p>

				<form>
					<label>
						Name auf der Karte:
						<input type="text" placeholder="Max Mustermann" />
					</label>

					<label>
						Kartennummer:
						<input type="text" placeholder="1234 5678 9012 3456" />
					</label>

					<label>
						Ablaufdatum:
						<input type="text" placeholder="MM/YY" />
					</label>

					<label>
						CVC:
						<input type="text" placeholder="123" />
					</label>

					<label>
						Rechnungsadresse (optional):
						<input type="text" placeholder="Straße und Hausnummer" />
					</label>

					<div className="form-actions">
						<button type="button" className="back" onClick={onBack}>
							Zurück
						</button>
						<button type="button" className="submit" onClick={onSubmit}>
							Buchung senden
						</button>
					</div>
				</form>
			</div>
		</div>
	);
}
