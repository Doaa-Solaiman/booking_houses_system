import React from 'react';

//import "./überUns.css";

export const AboutUs = ({onBack}: {onBack: () => void}) => {
	return (
	<div className='aboutUs-background'>
		<div className="about-us-container">
			<h2 className="about-us-heading">Wer sind wir?</h2>
			<p className="about-us-paragraph">
				Wir sind ein Service für die Buchung von Ferienhäusern, der Ihnen eine unvergleichliche Auswahl an hochwertigen Unterkünften bietet. <br/>
				Unsere Plattform präsentiert Ihnen sorgfältig ausgewählte Ferienhäuser in atemberaubenden Gegenden, die ideal für einen entspannenden Urlaub oder ein aufregendes Abenteuer sind.<br/>
				Ob Sie eine gemütliche Hütte in den Bergen, eine luxuriöse Villa am Strand oder ein charmantes Landhaus suchen, wir haben für jeden Geschmack und jedes Budget etwas Passendes.<br/>
				
				Unser engagiertes Team sorgt dafür, dass jedes Ferienhaus strengen Qualitätskontrollen unterzogen wird, <br/>
				um Ihnen höchsten Komfort und unvergessliche Erlebnisse zu garantieren.<br/>
				Nutzen Sie unsere benutzerfreundliche Suchfunktion, um die perfekte Unterkunft für Ihre Bedürfnisse <br/>
				zu finden und buchen Sie Ihren nächsten Traumurlaub mit nur wenigen Klicks. <br/>
				Lassen Sie sich von uns auf Ihrer Reise begleiten und erleben Sie unvergessliche Momente in Ihren Wunschdestinationen.
			</p>
			<button onClick={onBack} className="back-button">Zurück</button>
		</div>
	</div>
	);
}
