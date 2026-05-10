

import React, { createContext, useState, useContext } from 'react';
import translations from './translation';

const LanguageContext = React.createContext();

export const LanguageProvider = ({ children }) => {
const [language, setLanguage] = React.useState('en');

const translate = (key) => {
	const keys = key.split('.');
	let translation = translations[language];

	keys.forEach(k => {
	translation = translation[k];
	});

	return translation;
};

return (
	<LanguageContext.Provider value={{ language, setLanguage, translate }}>
	{children}
	</LanguageContext.Provider>
);
};

export const useLanguage = () => React.useContext(LanguageContext);
